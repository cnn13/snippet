package snip;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "snip",
        mixinStandardHelpOptions = true,
        version = "snip 1.0.0",
        description = "Local code-snippet manager with tags and search.",
        subcommands = {
                SnipCli.Add.class,
                SnipCli.List.class,
                SnipCli.Show.class,
                SnipCli.Search.class,
                SnipCli.Rm.class,
                SnipCli.Tag.class
        }
)
public final class SnipCli implements Callable<Integer> {

    public static void main(String[] args) {
        System.exit(new CommandLine(new SnipCli()).execute(args));
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    private static Store store() { return new Store(); }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    @Command(name = "add", description = "Add a new snippet. Body is read from --file, --body, or stdin.")
    static class Add implements Callable<Integer> {
        @Option(names = {"-t", "--title"}, required = true) String title;
        @Option(names = {"-l", "--lang"}, description = "Language, e.g. java, py, sh") String language = "";
        @Option(names = {"-g", "--tag"}, description = "Tag (repeatable)") java.util.List<String> tags = new ArrayList<>();
        @Option(names = {"-b", "--body"}, description = "Snippet body as a string") String body;
        @Option(names = {"-f", "--file"}, description = "Read body from file") Path file;

        public Integer call() throws Exception {
            String text = body;
            if (text == null && file != null) {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } else if (text == null) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                    String ln;
                    while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
                }
                text = sb.toString();
            }
            Store s = store();
            java.util.List<Snippet> all = s.all();
            Snippet sn = new Snippet(shortId(), title, language, tags, text, Instant.now());
            all.add(sn);
            s.save(all);
            System.out.println("added " + sn.id + "  " + title);
            return 0;
        }
    }

    @Command(name = "list", aliases = "ls", description = "List all snippets.")
    static class List implements Callable<Integer> {
        @Option(names = {"-g", "--tag"}, description = "Filter by tag") String tag;
        @Option(names = {"-l", "--lang"}, description = "Filter by language") String language;

        public Integer call() {
            java.util.List<Snippet> items = store().all();
            items.sort(Comparator.comparing((Snippet x) -> x.created).reversed());
            for (Snippet s : items) {
                if (tag != null && (s.tags == null || !s.tags.contains(tag))) continue;
                if (language != null && !language.equalsIgnoreCase(s.language)) continue;
                String tagStr = s.tags == null || s.tags.isEmpty() ? "" : " [" + String.join(",", s.tags) + "]";
                System.out.printf("%s  %-16s  %-6s  %s%s%n",
                        s.id, DATE.format(s.created), s.language, s.title, tagStr);
            }
            return 0;
        }
    }

    @Command(name = "show", description = "Print a snippet body by id prefix.")
    static class Show implements Callable<Integer> {
        @Parameters(index = "0", description = "id or id prefix") String id;

        public Integer call() {
            Optional<Snippet> opt = store().find(id);
            if (opt.isEmpty()) { System.err.println("no unique match for '" + id + "'"); return 1; }
            Snippet s = opt.get();
            System.out.println("# " + s.title + "  (" + s.id + ")");
            if (!s.language.isEmpty()) System.out.println("# lang: " + s.language);
            if (s.tags != null && !s.tags.isEmpty()) System.out.println("# tags: " + String.join(", ", s.tags));
            System.out.println();
            System.out.print(s.body);
            return 0;
        }
    }

    @Command(name = "search", description = "Full-text search over title, body and tags.")
    static class Search implements Callable<Integer> {
        @Parameters(index = "0", description = "query (case-insensitive substring)") String query;
        @Option(names = {"-c", "--content"}, description = "Print matching body lines") boolean content;

        public Integer call() {
            String q = query.toLowerCase(Locale.ROOT);
            for (Snippet s : store().all()) {
                boolean hitTitle = s.title.toLowerCase(Locale.ROOT).contains(q);
                boolean hitTag = s.tags != null && s.tags.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(q));
                boolean hitBody = s.body != null && s.body.toLowerCase(Locale.ROOT).contains(q);
                if (!(hitTitle || hitTag || hitBody)) continue;
                System.out.printf("%s  %s%n", s.id, s.title);
                if (content && hitBody) {
                    for (String ln : s.body.split("\n")) {
                        if (ln.toLowerCase(Locale.ROOT).contains(q)) System.out.println("    " + ln);
                    }
                }
            }
            return 0;
        }
    }

    @Command(name = "rm", description = "Delete a snippet by id prefix.")
    static class Rm implements Callable<Integer> {
        @Parameters(index = "0") String id;
        public Integer call() {
            Store st = store();
            java.util.List<Snippet> all = st.all();
            Optional<Snippet> opt = st.find(id);
            if (opt.isEmpty()) { System.err.println("no unique match for '" + id + "'"); return 1; }
            all.removeIf(s -> s.id.equals(opt.get().id));
            st.save(all);
            System.out.println("removed " + opt.get().id);
            return 0;
        }
    }

    @Command(name = "tag", description = "Add or remove tags on a snippet.")
    static class Tag implements Callable<Integer> {
        @Parameters(index = "0") String id;
        @Option(names = {"-a", "--add"}) java.util.List<String> add = new ArrayList<>();
        @Option(names = {"-r", "--remove"}) java.util.List<String> remove = new ArrayList<>();

        public Integer call() {
            Store st = store();
            java.util.List<Snippet> all = st.all();
            Optional<Snippet> opt = st.find(id);
            if (opt.isEmpty()) { System.err.println("no unique match for '" + id + "'"); return 1; }
            Snippet s = opt.get();
            if (s.tags == null) s.tags = new ArrayList<>();
            for (String t : add) if (!s.tags.contains(t)) s.tags.add(t);
            s.tags.removeAll(remove);
            st.save(all);
            System.out.println(s.id + "  tags: " + String.join(",", s.tags));
            return 0;
        }
    }
}
