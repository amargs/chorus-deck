package org.amargs.chorusdeck.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.amargs.chorusdeck.config.AppConfig;
import org.amargs.chorusdeck.deck.DeckService;
import org.amargs.chorusdeck.deck.Interval;
import org.amargs.chorusdeck.text.BoldMarkup;
import org.amargs.chorusdeck.index.SongCfg;
import org.amargs.chorusdeck.index.SongIndex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Local page that builds a song deck from the index. */
public final class SongDeckServer {

    private static final Logger LOG = LogManager.getLogger(SongDeckServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private SongDeckServer() {
    }

    public static Javalin create(Path stateDirectory) {
        LibraryStore library = new LibraryStore(stateDirectory);
        AtomicReference<Path> lastOutput = new AtomicReference<>();
        Javalin app = Javalin.create(javalin -> {
            javalin.showJavalinBanner = false;
            if (SongDeckServer.class.getResource("/ui/index.html") != null) {
                javalin.staticFiles.add("/ui", Location.CLASSPATH);
                javalin.spaRoot.addFile("/", "/ui/index.html", Location.CLASSPATH);
            }
        });
        app.get("/api/library", ctx -> ctx.json(libraryJson(library.status())));
        app.post("/api/library", ctx -> {
            JsonNode body = readObject(ctx.body(), "The library request must be an object.");
            JsonNode index = body.get("index");
            if (index == null || !index.isTextual() || index.asText().isBlank()) {
                throw new IllegalArgumentException("The library request needs an index path.");
            }
            ctx.json(libraryJson(new LibraryStore.Status(library.save(Path.of(index.asText())), false)));
        });
        app.get("/api/songs", ctx -> ctx.json(SongDeckRequests.songs(SongIndex.read(library.requireIndex()))));
        app.get("/api/fonts", ctx -> ctx.json(Map.of("missing", BoldMarkup.missingFontLinks())));
        TodayTheme today = new TodayTheme(TodayTheme::httpGet);
        app.get("/api/today", ctx -> {
            TodayTheme.Answer answer = today.resolve(LocalDate.now(), ctx.cookie(TodayTheme.COOKIE));
            if (answer.store()) {
                ctx.header("Set-Cookie", TodayTheme.setCookie(answer.snapshot()));
            }
            ctx.json(Map.of("date", answer.snapshot().date(), "season", answer.snapshot().season()));
        });
        app.get("/api/directory", ctx -> {
            LibraryStore.Status status = library.status();
            Path folder = status.index() == null ? null : status.index().getParent();
            ctx.json(folder == null ? Map.of() : Map.of("directory", folder.toString()));
        });
        app.post("/api/choose-index", ctx -> {
            try {
                LibraryStore.Status status = library.status();
                Path chosen = FolderChooser.chooseFile(browseStart(status));
                if (chosen == null) {
                    ctx.json(Map.of("cancelled", true));
                } else {
                    ctx.json(libraryJson(new LibraryStore.Status(library.save(chosen), false)));
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                String message = e.getMessage();
                throw new IOException(message == null || message.isBlank() ? "Could not open the file chooser." : message, e);
            }
        });
        app.post("/api/choose-folder", ctx -> {
            try {
                LibraryStore.Status status = library.status();
                Path chosen = FolderChooser.choose(browseStart(status));
                if (chosen == null) {
                    ctx.json(Map.of("cancelled", true));
                } else {
                    ctx.json(Map.of("directory", chosen.toString()));
                }
            } catch (Exception e) {
                String message = e.getMessage();
                throw new IOException(message == null || message.isBlank() ? "Could not open the folder chooser." : message, e);
            }
        });
        app.post("/api/generate", ctx -> {
            JsonNode body;
            try {
                body = JSON.readTree(ctx.body());
            } catch (IOException e) {
                throw new IllegalArgumentException("The generate request must be an object.");
            }
            Path index = library.requireIndex();
            List<SongCfg> songs = SongIndex.read(index);
            Path besideIndex = index.getParent() == null ? stateDirectory : index.getParent();
            SongDeckRequests.Ready ready = SongDeckRequests.parse(body, songs, besideIndex);
            DeckService.Generated generated = new DeckService().generateSongs(
                    new AppConfig(null, index, PackagedTemplate.file(), null).withOutput(ready.output()),
                    new DeckService.Selection.Ids(ready.ids()),
                    ready.cover(),
                    Interval.blank());
            Path written = ready.output().toAbsolutePath().normalize();
            lastOutput.set(written);
            ctx.json(Map.of(
                    "output", written.toString(),
                    "slides", generated.slides()));
        });
        app.post("/api/open", ctx -> {
            JsonNode body = readObject(ctx.body(), "The open request must be an object.");
            JsonNode pathNode = body.get("path");
            JsonNode whatNode = body.get("what");
            if (pathNode == null || !pathNode.isTextual() || whatNode == null || !whatNode.isTextual()) {
                throw new IllegalArgumentException("The open request needs a path and what.");
            }
            LocalFiles.OpenPlan plan = LocalFiles.plan(Path.of(pathNode.asText()), whatNode.asText(), lastOutput.get());
            LocalFiles.launch(plan);
            ctx.json(Map.of("ok", true));
        });
        app.exception(IllegalArgumentException.class, (error, ctx) ->
                ctx.status(400).json(Map.of("error", error.getMessage())));
        app.exception(IOException.class, (error, ctx) ->
                ctx.status(500).json(Map.of("error", error.getMessage())));
        return app;
    }

    /** Opens the page and returns when that window closes. */
    public static void serve(Path stateDirectory, int port) throws Exception {
        serve(stateDirectory, port, AppWindow::open);
    }

    static void serve(Path stateDirectory, int port, Window window) throws Exception {
        Javalin app = create(stateDirectory);
        try {
            app.start("127.0.0.1", port);
            URI uri = URI.create("http://127.0.0.1:" + app.port() + "/");
            LOG.info("Song deck at " + uri);
            window.open(uri, stateDirectory.resolve("browser-profile"));
        } finally {
            app.stop();
        }
    }

    @FunctionalInterface
    interface Window {
        void open(URI uri, Path profile) throws Exception;
    }

    private static Path browseStart(LibraryStore.Status status) {
        if (status.index() != null && status.index().getParent() != null) {
            return status.index().getParent();
        }
        return Path.of(System.getProperty("user.home"));
    }

    private static JsonNode readObject(String body, String message) {
        JsonNode node;
        try {
            node = JSON.readTree(body);
        } catch (IOException e) {
            throw new IllegalArgumentException(message);
        }
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(message);
        }
        return node;
    }

    private static Map<String, Object> libraryJson(LibraryStore.Status status) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (status.index() != null) {
            body.put("index", status.index().toString());
            if (status.index().getParent() != null) {
                body.put("directory", status.index().getParent().toString());
            }
        }
        if (status.missing()) {
            body.put("missing", true);
        }
        return body;
    }
}
