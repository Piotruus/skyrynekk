package pl.skypvp.market;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public abstract class BaseGui implements InventoryHolder {

    protected Inventory inv;

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public abstract void click(Player p, int slot, ClickType type);
}
