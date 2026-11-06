package pl.skypvp.market;

public enum SortMode {
    NEWEST("od najnowszych"),
    PRICE_DESC("od największej ceny"),
    PRICE_ASC("od najmniejszej ceny");

    public final String label;

    SortMode(String label) {
        this.label = label;
    }

    public SortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public SortMode prev() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }
}
