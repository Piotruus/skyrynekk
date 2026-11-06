package pl.skypvp.market;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketCommand implements CommandExecutor, TabCompleter {

    private final MarketManager m;

    public MarketCommand(MarketManager m) {
        this.m = m;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("market.admin")) {
                sender.sendMessage(m.c(m.msg("no-permission")));
                return true;
            }
            m.plugin().reloadConfig();
            m.loadSearch();
            sender.sendMessage(m.c(m.msg("reloaded")));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Tylko dla graczy.");
            return true;
        }
        if (a.length == 0) {
            m.send(p, "usage");
            return true;
        }
        switch (a[0].toLowerCase()) {
            // wejscie na rynek zawsze zaczyna od: wszystkie przedmioty, od najnowszych
            case "open", "otworz", "otwórz" -> m.openMarketFresh(p);
            case "sell", "wystaw" -> {
                if (a.length < 2) {
                    m.send(p, "usage-sell");
                    return true;
                }
                Long price = Util.parsePrice(a[1]);
                if (price == null) {
                    m.send(p, "invalid-price");
                    return true;
                }
                m.sell(p, price);
            }
            case "search", "szukaj" -> {
                if (a.length < 2) {
                    m.send(p, "usage-search");
                    return true;
                }
                ViewState st = m.state(p);
                st.reset();
                st.search = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                m.openMarket(p);
            }
            default -> m.send(p, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            boolean pl = label.equalsIgnoreCase("rynek");
            List<String> opts = new ArrayList<>(pl ? List.of("otworz", "wystaw", "szukaj") : List.of("open", "sell", "search"));
            if (s.hasPermission("market.admin")) opts.add("reload");
            for (String o : opts) if (o.startsWith(a[0].toLowerCase())) out.add(o);
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("sell") || a[0].equalsIgnoreCase("wystaw"))) {
            out.addAll(List.of("100", "1k", "100k", "1m"));
        }
        return out;
    }
}
