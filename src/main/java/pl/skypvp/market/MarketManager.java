package pl.skypvp.market;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MarketManager {

    private final MarketPlugin plugin;
    private final File file;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final SearchIndex index = new SearchIndex();
    private Economy economy;

    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> claims = new HashMap<>();
    private final Map<UUID, List<String>> sales = new HashMap<>();
    private final Map<UUID, ViewState> views = new HashMap<>();

    /** gracze, ktorzy wpisuja nazwe na czacie (tryb CHAT) */
    public final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();
    /** gracze, ktorzy maja otwarte kowadlo wyszukiwania (tryb ANVIL) */
    public final Set<UUID> searching = ConcurrentHashMap.newKeySet();
    public final Map<UUID, String> searchText = new ConcurrentHashMap<>();

    private long nextId = 1;

    public MarketManager(MarketPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public MarketPlugin plugin() {
        return plugin;
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    public FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void loadSearch() {
        index.load(new File(plugin.getDataFolder(), "search-pl.txt"), plugin.getLogger());
    }

    // ------------------------------------------------------------ teksty

    public Component c(String s) {
        return legacy.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public String msg(String key, String... pairs) {
        String s = cfg().getString("messages." + key, "&cBrak wiadomosci: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) s = s.replace(pairs[i], pairs[i + 1]);
        return s;
    }

    public void send(Player p, String key, String... pairs) {
        p.sendMessage(c(msg(key, pairs)));
    }

    /** Wiadomosc na srodku ekranu */
    public void title(Player p, String key, String... pairs) {
        p.showTitle(Title.title(Component.text(" "), c(msg(key, pairs)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
    }

    // ------------------------------------------------------------ itemy GUI

    public ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(this::c).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack filler() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack fillerOf(Material mat) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    public long balance(Player p) {
        return economy == null ? 0 : (long) Math.floor(economy.getBalance(p));
    }

    @SuppressWarnings("deprecation")
    public ItemStack button(String key, Material def, String name, List<String> lore) {
        Material mat = Material.matchMaterial(cfg().getString("gui.buttons." + key + ".material", def.name()));
        if (mat == null) mat = def;
        ItemStack it = item(mat, name, lore);
        int model = cfg().getInt("gui.buttons." + key + ".model-data", 0);
        if (model > 0) {
            ItemMeta meta = it.getItemMeta();
            meta.setCustomModelData(model);
            it.setItemMeta(meta);
        }
        return it;
    }

    public ItemStack withLore(ItemStack base, String... lines) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String s : lines) lore.add(c(s));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public ItemStack listingItem(Listing l) {
        long left = l.expiresAt - System.currentTimeMillis();
        return withLore(l.item, "",
                "&7Cena: &a" + Util.formatPrice(l.price),
                "&7Sprzedający: &f" + l.sellerName,
                "&7Pozostało: &f" + Util.formatTime(left),
                "",
                "&eKliknij, aby kupić!");
    }

    // ------------------------------------------------------------ dane

    public ViewState state(Player p) {
        return views.computeIfAbsent(p.getUniqueId(), k -> new ViewState());
    }

    public int countBy(UUID id) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller.equals(id)) n++;
        return n;
    }

    public List<Listing> listingsOf(UUID id) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller.equals(id)) out.add(l);
        return out;
    }

    public List<ItemStack> claims(UUID id) {
        return new ArrayList<>(claims.getOrDefault(id, List.of()));
    }

    /** Oferta, ktora jest teraz widoczna na rynku (po 10s opoznienia, przed wygasnieciem). */
    public Listing visibleListing(long id) {
        Listing l = listings.get(id);
        long now = System.currentTimeMillis();
        if (l == null || now < l.visibleFrom || now >= l.expiresAt) return null;
        return l;
    }

    public List<Listing> query(Player viewer, ViewState st) {
        long now = System.currentTimeMillis();
        double balance = (st.affordable && economy != null) ? economy.getBalance(viewer) : 0;
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            if (now < l.visibleFrom || now >= l.expiresAt) continue;
            if (st.affordable && l.price > balance) continue;
            if (st.category != null && Category.of(l.item) != st.category) continue;
            if (st.search != null && !index.matches(l.item, st.search)) continue;
            out.add(l);
        }
        switch (st.sort) {
            case NEWEST -> out.sort(Comparator.comparingLong((Listing l) -> l.visibleFrom).reversed());
            case PRICE_DESC -> out.sort(Comparator.comparingLong((Listing l) -> l.price).reversed());
            case PRICE_ASC -> out.sort(Comparator.comparingLong((Listing l) -> l.price));
        }
        return out;
    }

    // ------------------------------------------------------------ otwieranie GUI (1 tick pozniej)

    /** Otwiera rynek z zachowaniem biezacych filtrow (np. powrot z "Twoje przedmioty"). */
    public void openMarket(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(new MarketGui(this, p).getInventory()));
    }

    /** Otwiera rynek od zera: wszystkie przedmioty, od najnowszych. */
    public void openMarketFresh(Player p) {
        state(p).reset();
        openMarket(p);
    }

    public void openMine(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(new MyItemsGui(this, p).getInventory()));
    }

    public void openConfirm(Player p, Listing l) {
        Bukkit.getScheduler().runTask(plugin, () -> p.openInventory(new ConfirmGui(this, l, p).getInventory()));
    }

    // ------------------------------------------------------------ wyszukiwanie

    public void openSearch(Player p) {
        if (cfg().getString("gui.search-mode", "ANVIL").equalsIgnoreCase("CHAT")) {
            startChatSearch(p);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            InventoryView view = p.openAnvil(null, true);
            if (view == null) {
                startChatSearch(p);
                return;
            }
            searching.add(p.getUniqueId());
            searchText.put(p.getUniqueId(), "");
            view.getTopInventory().setItem(0, item(Material.PAPER, " ", null));
        });
    }

    private void startChatSearch(Player p) {
        awaitingSearch.add(p.getUniqueId());
        p.closeInventory();
        send(p, "search-prompt");
    }

    public void applySearch(Player p, String text) {
        ViewState st = state(p);
        st.search = text.isBlank() ? null : text.trim();
        st.page = 0;
        p.closeInventory();
        openMarket(p);
    }

    private Component itemName(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.displayName();
        return Component.translatable(it.getType().translationKey());
    }

    /** Przedmiot pokazywany w polu wyniku kowadla - odswieza sie po kazdej literce. */
    public ItemStack searchPreview(Player p, String text) {
        if (text.isBlank()) {
            return item(Material.PAPER, "&7Wpisz nazwę przedmiotu...",
                    List.of("&8Wyniki pojawiają się w trakcie pisania.",
                            "&8Możesz pisać po polsku: miecz, kilof, klata..."));
        }
        ViewState tmp = new ViewState();
        tmp.search = text.trim();
        List<Listing> res = query(p, tmp);
        if (res.isEmpty()) {
            return item(Material.BARRIER, "&cBrak wyników",
                    List.of("&7Szukasz: &f" + text.trim(), "", "&8Spróbuj innej nazwy."));
        }

        ItemStack icon = new ItemStack(res.get(0).item.getType());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(c("&aZnaleziono: &f" + res.size() + " &aofert"));
        List<Component> lore = new ArrayList<>();
        lore.add(c("&7Szukasz: &f" + text.trim()));
        lore.add(Component.empty());
        int shown = Math.min(8, res.size());
        for (int i = 0; i < shown; i++) {
            Listing l = res.get(i);
            lore.add(Component.text("• ", NamedTextColor.GRAY)
                    .append(itemName(l.item).colorIfAbsent(NamedTextColor.WHITE))
                    .append(Component.text(" x" + l.item.getAmount(), NamedTextColor.DARK_GRAY))
                    .append(Component.text("  " + Util.formatPrice(l.price), NamedTextColor.GREEN))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (res.size() > shown) lore.add(c("&8... i " + (res.size() - shown) + " więcej"));
        lore.add(Component.empty());
        lore.add(c("&eKliknij, aby pokazać wszystkie!"));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        icon.setItemMeta(meta);
        return icon;
    }

    // ------------------------------------------------------------ wystawianie

    public void sell(Player p, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(p, "no-item");
            return;
        }
        long min = cfg().getLong("min-price", 1);
        long max = cfg().getLong("max-price", 1_000_000_000_000L);
        if (price < min || price > max) {
            send(p, "price-range", "{min}", Util.formatPrice(min), "{max}", Util.formatPrice(max));
            return;
        }
        int maxListings = cfg().getInt("max-listings-per-player", 10);
        if (countBy(p.getUniqueId()) >= maxListings) {
            send(p, "limit", "{max}", String.valueOf(maxListings));
            return;
        }
        int delay = cfg().getInt("sell-delay-seconds", 10);
        long visible = System.currentTimeMillis() + delay * 1000L;
        long expires = visible + cfg().getLong("duration-hours", 24) * 3_600_000L;

        Listing l = new Listing(nextId++, p.getUniqueId(), p.getName(), hand.clone(), price, visible, expires, false);
        p.getInventory().setItemInMainHand(null);
        listings.put(l.id, l);
        save();
        send(p, "pending", "{seconds}", String.valueOf(delay));
    }

    public void cancel(Player p, long id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller.equals(p.getUniqueId())) return;
        if (p.getInventory().firstEmpty() == -1) {
            send(p, "inv-full");
            return;
        }
        listings.remove(id);
        p.getInventory().addItem(l.item.clone());
        save();
        send(p, "cancelled");
    }

    public void claim(Player p, int index) {
        List<ItemStack> list = claims.get(p.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) return;
        if (p.getInventory().firstEmpty() == -1) {
            send(p, "inv-full");
            return;
        }
        ItemStack it = list.remove(index);
        if (list.isEmpty()) claims.remove(p.getUniqueId());
        p.getInventory().addItem(it);
        save();
    }

    // ------------------------------------------------------------ kupowanie

    public void buy(Player buyer, long id) {
        Listing l = visibleListing(id);
        if (l == null) {
            send(buyer, "gone");
            return;
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            send(buyer, "own-item");
            return;
        }
        if (economy == null || economy.getBalance(buyer) < l.price) {
            send(buyer, "no-money");
            return;
        }
        if (buyer.getInventory().firstEmpty() == -1) {
            send(buyer, "inv-full");
            return;
        }
        EconomyResponse r = economy.withdrawPlayer(buyer, l.price);
        if (!r.transactionSuccess()) {
            send(buyer, "no-money");
            return;
        }

        double pct = cfg().getDouble("commission-percent", 5.0);
        long fee = (long) Math.floor(l.price * pct / 100.0);
        long net = l.price - fee;
        economy.depositPlayer(Bukkit.getOfflinePlayer(l.seller), net);

        listings.remove(id);
        buyer.getInventory().addItem(l.item.clone());
        send(buyer, "bought", "{price}", Util.formatPrice(l.price));

        String pctText = pct == Math.floor(pct) ? String.valueOf((long) pct) : String.valueOf(pct);
        Player seller = Bukkit.getPlayer(l.seller);
        if (seller != null) {
            title(seller, "sold", "{price}", Util.formatPrice(l.price));
            send(seller, "sold-chat", "{net}", Util.formatPrice(net), "{commission}", pctText);
        } else {
            sales.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.price + ":" + net);
        }
        save();
    }

    // ------------------------------------------------------------ zadania i wejscie gracza

    public void tick() {
        long now = System.currentTimeMillis();
        boolean dirty = false;
        Iterator<Listing> it = listings.values().iterator();
        while (it.hasNext()) {
            Listing l = it.next();
            if (now >= l.expiresAt) {
                claims.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
                it.remove();
                dirty = true;
                Player p = Bukkit.getPlayer(l.seller);
                if (p != null) send(p, "expired");
                continue;
            }
            if (!l.announced && now >= l.visibleFrom) {
                l.announced = true;
                dirty = true;
                Player p = Bukkit.getPlayer(l.seller);
                if (p != null) title(p, "listed", "{price}", Util.formatPrice(l.price));
            }
        }
        if (dirty) save();
    }

    public void onJoin(Player p) {
        List<String> list = sales.remove(p.getUniqueId());
        if (list != null) {
            double pct = cfg().getDouble("commission-percent", 5.0);
            String pctText = pct == Math.floor(pct) ? String.valueOf((long) pct) : String.valueOf(pct);
            for (String s : list) {
                String[] parts = s.split(":");
                send(p, "sold", "{price}", Util.formatPrice(Long.parseLong(parts[0])));
                send(p, "sold-chat", "{net}", Util.formatPrice(Long.parseLong(parts[1])), "{commission}", pctText);
            }
            save();
        }
        if (claims.containsKey(p.getUniqueId())) send(p, "claims-reminder");
    }

    // ------------------------------------------------------------ zapis / odczyt

    private static String enc(ItemStack it) {
        return Base64.getEncoder().encodeToString(it.serializeAsBytes());
    }

    private static ItemStack dec(String s) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("nextId", nextId);
        for (Listing l : listings.values()) {
            String p = "listings." + l.id;
            y.set(p + ".seller", l.seller.toString());
            y.set(p + ".name", l.sellerName);
            y.set(p + ".item", enc(l.item));
            y.set(p + ".price", l.price);
            y.set(p + ".visibleFrom", l.visibleFrom);
            y.set(p + ".expiresAt", l.expiresAt);
            y.set(p + ".announced", l.announced);
        }
        for (Map.Entry<UUID, List<ItemStack>> e : claims.entrySet()) {
            y.set("claims." + e.getKey(), e.getValue().stream().map(MarketManager::enc).toList());
        }
        for (Map.Entry<UUID, List<String>> e : sales.entrySet()) {
            y.set("sales." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Nie udalo sie zapisac data.yml: " + ex.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextId = y.getLong("nextId", 1);

        ConfigurationSection ls = y.getConfigurationSection("listings");
        if (ls != null) {
            for (String k : ls.getKeys(false)) {
                try {
                    ConfigurationSection s = ls.getConfigurationSection(k);
                    Listing l = new Listing(Long.parseLong(k), UUID.fromString(s.getString("seller")),
                            s.getString("name", "?"), dec(s.getString("item")), s.getLong("price"),
                            s.getLong("visibleFrom"), s.getLong("expiresAt"), s.getBoolean("announced"));
                    listings.put(l.id, l);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Pominieto uszkodzona oferte: " + k);
                }
            }
        }
        ConfigurationSection cs = y.getConfigurationSection("claims");
        if (cs != null) {
            for (String k : cs.getKeys(false)) {
                List<ItemStack> list = new ArrayList<>();
                for (String s : cs.getStringList(k)) {
                    try {
                        list.add(dec(s));
                    } catch (Exception ignored) {
                    }
                }
                if (!list.isEmpty()) claims.put(UUID.fromString(k), list);
            }
        }
        ConfigurationSection ss = y.getConfigurationSection("sales");
        if (ss != null) {
            for (String k : ss.getKeys(false)) {
                sales.put(UUID.fromString(k), new ArrayList<>(ss.getStringList(k)));
            }
        }
    }
}
