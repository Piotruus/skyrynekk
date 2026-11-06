package pl.skypvp.market;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Util {

    private static final Pattern NUM = Pattern.compile("^\\d+([.,]\\d+)?[kmb]?$");

    private Util() {
    }

    /** 100 -> 100, 1k -> 1000, 100k -> 100000, 1m -> 1000000, 1.5k -> 1500. Zwraca null przy bledzie. */
    public static Long parsePrice(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (!NUM.matcher(s).matches()) return null;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1e3;
        else if (last == 'm') mult = 1e6;
        else if (last == 'b') mult = 1e9;
        if (!Character.isDigit(last)) s = s.substring(0, s.length() - 1);
        long r = Math.round(Double.parseDouble(s.replace(',', '.')) * mult);
        return r <= 0 ? null : r;
    }

    /** 1000 -> 1k, 100000 -> 100k, 1000000 -> 1m */
    public static String formatPrice(long v) {
        if (v >= 1_000_000_000L) return scaled(v, 1_000_000_000L, "b");
        if (v >= 1_000_000L) return scaled(v, 1_000_000L, "m");
        if (v >= 1_000L) return scaled(v, 1_000L, "k");
        return Long.toString(v);
    }

    private static String scaled(long v, long unit, String suffix) {
        long whole = v / unit;
        long frac = (v % unit) * 100 / unit;
        String s = Long.toString(whole);
        if (frac > 0) {
            String f = String.format("%02d", frac);
            if (f.endsWith("0")) f = f.substring(0, 1);
            s += "." + f;
        }
        return s + suffix;
    }

    public static String formatTime(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
