package snip;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Tiny line-oriented store: one snippet per record, body base64-encoded.
 * Format per line: id|epochSeconds|language|title|tag1,tag2|base64body
 * Chosen over JSON to keep the native-image reflection surface at zero.
 */
public final class Store {
    private final Path file;

    public Store() {
        String home = System.getProperty("user.home");
        Path dir = Paths.get(home, ".snip");
        try { Files.createDirectories(dir); } catch (IOException e) { throw new RuntimeException(e); }
        this.file = dir.resolve("snippets.db");
    }

    public List<Snippet> all() {
        if (!Files.exists(file)) return new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            List<Snippet> out = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line.isBlank()) continue;
                out.add(parse(line));
            }
            return out;
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void save(List<Snippet> items) {
        StringBuilder sb = new StringBuilder();
        for (Snippet s : items) sb.append(serialize(s)).append('\n');
        try {
            Path tmp = file.resolveSibling("snippets.db.tmp");
            Files.writeString(tmp, sb.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public Optional<Snippet> find(String idPrefix) {
        List<Snippet> matches = new ArrayList<>();
        for (Snippet s : all()) if (s.id.startsWith(idPrefix)) matches.add(s);
        if (matches.size() == 1) return Optional.of(matches.get(0));
        return Optional.empty();
    }

    private static String serialize(Snippet s) {
        String tags = s.tags == null ? "" : String.join(",", s.tags);
        String body64 = Base64.getEncoder().encodeToString(
                s.body == null ? new byte[0] : s.body.getBytes(StandardCharsets.UTF_8));
        return String.join("|",
                s.id,
                Long.toString(s.created.getEpochSecond()),
                nz(s.language),
                escape(s.title),
                tags,
                body64);
    }

    private static Snippet parse(String line) {
        String[] p = line.split("\\|", 6);
        Snippet s = new Snippet();
        s.id = p[0];
        s.created = Instant.ofEpochSecond(Long.parseLong(p[1]));
        s.language = p[2];
        s.title = unescape(p[3]);
        s.tags = p[4].isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(p[4].split(",")));
        s.body = new String(Base64.getDecoder().decode(p[5]), StandardCharsets.UTF_8);
        return s;
    }

    private static String nz(String v) { return v == null ? "" : v; }
    private static String escape(String v) { return v == null ? "" : v.replace("\\", "\\\\").replace("|", "\\p"); }
    private static String unescape(String v) { return v.replace("\\p", "|").replace("\\\\", "\\"); }
}
