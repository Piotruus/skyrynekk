package pl.skypvp.market;

public class ViewState {
    public SortMode sort = SortMode.NEWEST;
    public boolean affordable = false;
    public Category category = null;
    public String search = null;
    public int page = 0;

    /** Wraca do ustawien domyslnych: wszystkie przedmioty, od najnowszych. */
    public void reset() {
        sort = SortMode.NEWEST;
        affordable = false;
        category = null;
        search = null;
        page = 0;
    }
}
