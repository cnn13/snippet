package snip;

import java.time.Instant;
import java.util.List;

public final class Snippet {
    public String id;
    public String title;
    public String language;
    public List<String> tags;
    public String body;
    public Instant created;

    public Snippet() {}

    public Snippet(String id, String title, String language, List<String> tags, String body, Instant created) {
        this.id = id;
        this.title = title;
        this.language = language;
        this.tags = tags;
        this.body = body;
        this.created = created;
    }
}
