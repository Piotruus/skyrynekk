package pl.skypvp.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyItemsGui extends BaseGui {

    private final MarketManager m;
    private final Player player;
    private final Map<Integer, Integer> claimSlots = new HashMap<>();
    private final Map<Integer, Long> listingSlots = new HashMap<>();

    public MyItemsGui(MarketManager m, Player player) {
        this.m = m;
        this.player = player;
        this.inv = Bukkit.createInventory(this, 54, m.c("&0Twoje przedmioty"));
        render();
    }

    private void render() {
        claimSlots.clear();
        listingSlots.clear();
        inv.clear();
        ItemStack filler = m.filler();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        List<ItemStack> claims = m.claims(player.getUniqueId());
        for (int i = 0; i < claims.size() && slot < 45; i++, slot++) {
            inv.setItem(slot, m.withLore(claims.get(i), "", "&eOferta wygasła", "&aKliknij, aby odebrać"));
            claimSlots.put(slot, i);
        }

        long now = System.currentTimeMillis();
        for (Listing l : m.listingsOf(player.getUniqueId())) {
            if (slot >= 45) break;
            String status = now < l.visibleFrom
                    ? "&eNa rynku za: &f" + Util.formatTime(l.visibleFrom - now)
                    : "&7Pozostało: &f" + Util.formatTime(l.expiresAt - now);
            inv.setItem(slot, m.withLore(l.item, "",
                    "&7Cena: &a" + Util.formatPrice(l.price), status, "", "&cKliknij, aby anulować"));
            listingSlots.put(slot, l.id);
            slot++;
        }

        inv.setItem(49, m.item(Material.ARROW, "&eWróć do rynku", null));
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (claimSlots.containsKey(slot)) {
            m.claim(p, claimSlots.get(slot));
            render();
        } else if (listingSlots.containsKey(slot)) {
            m.cancel(p, listingSlots.get(slot));
            render();
        } else if (slot == 49) {
            m.openMarket(p);
        }
    }
}
