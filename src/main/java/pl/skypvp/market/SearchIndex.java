package pl.skypvp.market;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Wyszukiwanie po polsku i angielsku. Kazde slowo z zapytania musi pasowac do przedmiotu:
 *  - jako poczatek angielskiego czlonu nazwy materialu (swo -> SWORD),
 *  - jako poczatek polskiego rdzenia ze slownika (mie -> miecz),
 *  - albo jako polskie slowo z koncowka (diamentowa -> rdzen "diament"),
 *  - albo jako fragment wlasnej nazwy przedmiotu.
 */
public class SearchIndex {

    private record Keywords(Set<String> english, Set<String> polish) {
    }

    private final Map<String, Set<String>> tokenStems = new HashMap<>();
    private final Map<Material, Set<String>> exact = new HashMap<>();
    private final Map<Material, Keywords> cache = new HashMap<>();

    public static String norm(String s) {
        if (s == null) return "";
        String x = s.toLowerCase(Locale.ROOT).replace('ł', 'l');
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return x.trim();
    }

    public void load(File file, Logger log) {
        tokenStems.clear();
        exact.clear();
        cache.clear();
        if (!file.exists()) return;
        try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key = line.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                Set<String> vals = new LinkedHashSet<>();
                for (String v : line.substring(eq + 1).split(",")) {
                    for (String w : norm(v).split("\\s+")) if (!w.isEmpty()) vals.add(w);
                }
                if (key.startsWith("@")) {
                    Material m = Material.matchMaterial(key.substring(1));
                    if (m != null) exact.computeIfAbsent(m, k -> new LinkedHashSet<>()).addAll(vals);
                } else {
                    tokenStems.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(vals);
                }
            }
        } catch (IOException e) {
            log.warning("Nie udalo sie wczytac slownika wyszukiwania: " + e.getMessage());
        }
    }

    private Keywords keywords(Material mat) {
        return cache.computeIfAbsent(mat, m -> {
            Set<String> en = new HashSet<>();
            Set<String> pl = new HashSet<>();
            for (String t : m.name().split("_")) {
                en.add(t.toLowerCase(Locale.ROOT));
                Set<String> s = tokenStems.get(t);
                if (s != null) pl.addAll(s);
            }
            Set<String> ex = exact.get(m);
            if (ex != null) pl.addAll(ex);
            return new Keywords(en, pl);
        });
    }

    private String displayName(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return norm(PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
        }
        return "";
    }

    public boolean matches(ItemStack it, String rawQuery) {
        String q = norm(rawQuery);
        if (q.isEmpty()) return true;
        Keywords k = keywords(it.getType());
        String display = displayName(it);
        for (String w : q.split("[\\s_]+")) {
            if (w.isEmpty()) continue;
            if (!wordMatches(w, k, display)) return false;
        }
        return true;
    }

    private boolean wordMatches(String w, Keywords k, String display) {
        for (String e : k.english()) {
            if (e.startsWith(w)) return true;
        }
        for (String p : k.polish()) {
            if (p.startsWith(w)) return true;
            if (p.length() >= 4 && w.startsWith(p)) return true;
        }
        return !display.isEmpty() && display.contains(w);
    }
}
