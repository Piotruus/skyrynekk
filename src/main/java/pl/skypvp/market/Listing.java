package pl.skypvp.market;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Listing {
    public final long id;
    public final UUID seller;
    public final String sellerName;
    public final ItemStack item;
    public final long price;
    public final long visibleFrom;
    public final long expiresAt;
    public boolean announced;

    public Listing(long id, UUID seller, String sellerName, ItemStack item, long price,
                   long visibleFrom, long expiresAt, boolean announced) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.visibleFrom = visibleFrom;
        this.expiresAt = expiresAt;
        this.announced = announced;
    }
}
