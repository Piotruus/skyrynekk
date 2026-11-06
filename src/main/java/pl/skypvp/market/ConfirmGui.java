package pl.skypvp.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Potwierdzenie zakupu:
 *   zielone (sloty 10-12) = KUP      przedmiot (slot 13)      czerwone (sloty 14-16) = ANULUJ
 */
public class ConfirmGui extends BaseGui {

    private final MarketManager m;
    private final long id;
    private final long openedAt = System.currentTimeMillis();

    public ConfirmGui(MarketManager m, Listing l, Player viewer) {
        this.m = m;
        this.id = l.id;
        this.inv = Bukkit.createInventory(this, 27, m.c("&8Potwierdź zakup"));

        ItemStack border = m.filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, border);

        long balance = m.balance(viewer);
        boolean enough = balance >= l.price;

        ItemStack confirm;
        if (enough) {
            confirm = m.item(Material.LIME_CONCRETE, "&a&lPOTWIERDŹ ZAKUP", List.of(
                    "&7Cena: &a" + Util.formatPrice(l.price),
                    "&7Twoje saldo: &f" + Util.formatPrice(balance),
                    "&7Po zakupie: &f" + Util.formatPrice(balance - l.price),
                    "",
                    "&eKliknij, aby kupić!"));
        } else {
            confirm = m.item(Material.GRAY_CONCRETE, "&c&lNIE STAĆ CIĘ", List.of(
                    "&7Cena: &a" + Util.formatPrice(l.price),
                    "&7Twoje saldo: &f" + Util.formatPrice(balance),
                    "&7Brakuje: &c" + Util.formatPrice(l.price - balance)));
        }
        ItemStack cancel = m.item(Material.RED_CONCRETE, "&c&lANULUJ",
                List.of("&7Wróć do rynku bez kupowania."));

        for (int s = 10; s <= 12; s++) inv.setItem(s, confirm);
        for (int s = 14; s <= 16; s++) inv.setItem(s, cancel);

        inv.setItem(13, m.withLore(l.item, "",
                "&7Cena: &a" + Util.formatPrice(l.price),
                "&7Sprzedający: &f" + l.sellerName));
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot >= 10 && slot <= 12) {
            // ochrona przed przypadkowym podwojnym kliknieciem tuz po otwarciu okna
            if (System.currentTimeMillis() - openedAt < 400) return;
            p.closeInventory();
            m.buy(p, id);
        } else if (slot >= 14 && slot <= 16) {
            m.openMarket(p);
        }
    }
}
