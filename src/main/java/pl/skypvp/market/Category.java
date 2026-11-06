package pl.skypvp.market;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public enum Category {
    CONSUMABLES("Mikstury i jedzenie", Material.POTION),
    WEAPONS("Bronie", Material.DIAMOND_SWORD),
    ARMOR("Zbroje", Material.DIAMOND_CHESTPLATE),
    TOOLS("Narzędzia", Material.DIAMOND_PICKAXE),
    OTHER("Inne", Material.TRIAL_KEY);

    public final String label;
    public final Material icon;

    Category(String label, Material icon) {
        this.label = label;
        this.icon = icon;
    }

    public static Category of(ItemStack it) {
        Material m = it.getType();
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || m == Material.ELYTRA || m == Material.SHIELD) return ARMOR;
        if (n.endsWith("_SWORD") || n.endsWith("ARROW") || m == Material.BOW || m == Material.CROSSBOW
                || m == Material.TRIDENT || m == Material.MACE) return WEAPONS;
        if (n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || m == Material.FISHING_ROD || m == Material.SHEARS || m == Material.FLINT_AND_STEEL) return TOOLS;
        if (n.endsWith("POTION") || m.isEdible()) return CONSUMABLES;
        return OTHER;
    }
}
