package pl.skypvp.market;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;

public class MarketListener implements Listener {

    private final MarketManager m;

    public MarketListener(MarketManager m) {
        this.m = m;
    }

    // ------------------------------------------------------------ GUI rynku

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // kowadlo wyszukiwania: klikniecie wyniku (slot 2) pokazuje wszystkie wyniki
        if (e.getWhoClicked() instanceof Player sp && m.searching.contains(sp.getUniqueId())
                && e.getView().getTopInventory() instanceof AnvilInventory) {
            e.setCancelled(true);
            if (e.getRawSlot() == 2) {
                m.applySearch(sp, m.searchText.getOrDefault(sp.getUniqueId(), ""));
            }
            return;
        }

        if (!(e.getView().getTopInventory().getHolder() instanceof BaseGui gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        gui.click((Player) e.getWhoClicked(), e.getSlot(), e.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof BaseGui) {
            e.setCancelled(true);
            return;
        }
        if (e.getWhoClicked() instanceof Player p && m.searching.contains(p.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------ wyszukiwanie na zywo (kowadlo)

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p) || !m.searching.contains(p.getUniqueId())) return;
        String text = e.getView().getRenameText();
        text = text == null ? "" : text.trim();
        m.searchText.put(p.getUniqueId(), text);
        e.setResult(m.searchPreview(p, text));
        e.getView().setRepairCost(0);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && m.searching.remove(p.getUniqueId())) {
            m.searchText.remove(p.getUniqueId());
            e.getInventory().clear(); // zeby przedmiot z kowadla nie wrocil do ekwipunku gracza
        }
    }

    // ------------------------------------------------------------ wyszukiwanie przez czat (tryb CHAT)

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!m.awaitingSearch.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(m.plugin(), () -> {
            m.awaitingSearch.remove(p.getUniqueId());
            if (!text.isEmpty() && !text.equalsIgnoreCase("anuluj")) {
                ViewState st = m.state(p);
                st.search = text;
                st.page = 0;
            }
            m.openMarket(p);
        });
    }

    // ------------------------------------------------------------ gracz

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        m.awaitingSearch.remove(e.getPlayer().getUniqueId());
        m.searching.remove(e.getPlayer().getUniqueId());
        m.searchText.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(m.plugin(), () -> {
            if (p.isOnline()) m.onJoin(p);
        }, 40L);
    }
}
