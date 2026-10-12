import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * News & Social Media Analytics Engine - web server (JDK only, no libraries).
 * Run Main (VS Code: open this file and click "Run"), then open http://localhost:8080
 * Optional argument: port number, e.g.  java -cp bin Main 9090
 */
public class Main {
    static final int MAX_BODY = 2 * 1024 * 1024;   // 2 MB
    static Path staticDir;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        staticDir = findStaticDir();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/sample", ex -> {
            if (!"GET".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use GET")); return; }
            sendJson(ex, 200, NLP.analyze(NLP.parseItems(SampleData.sampleText(), 5000)));
        });
        server.createContext("/api/sample-text", ex -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", SampleData.sampleText());
            sendJson(ex, 200, m);
        });
        server.createContext("/api/analyze", Main::handleAnalyze);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        String url = "http://localhost:" + port;
        System.out.println("\n  News & Social Media Analytics Engine running at " + url);
        System.out.println("  Static files: " + staticDir);
        System.out.println("  Press Ctrl+C (or the stop button) to stop.\n");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Desktop.getDesktop().browse(new URI(url));
        } catch (Throwable ignored) { /* headless: just open the URL manually */ }
    }

    // ------------------------------------------------------------------ handlers
    private static void handleAnalyze(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, error("Use POST")); return; }
        byte[] body;
        try (InputStream in = ex.getRequestBody()) { body = in.readNBytes(MAX_BODY + 1); }
        if (body.length > MAX_BODY) { sendJson(ex, 413, error("Input too large (max 2 MB).")); return; }
        String text;
        try {
            Object parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
            Object t = parsed instanceof Map ? ((Map<?, ?>) parsed).get("text") : null;
            text = t == null ? "" : t.toString();
        } catch (RuntimeException e) {
            sendJson(ex, 400, error("Invalid JSON."));
            return;
        }
        List<NLP.Item> items = NLP.parseItems(text, 5000);
        if (items.isEmpty()) { sendJson(ex, 400, error("No text found. Enter one post per line.")); return; }
        sendJson(ex, 200, NLP.analyze(items));
    }

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = staticDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(staticDir) || !Files.isRegularFile(file)) {
            byte[] msg = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, msg.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(msg); }
            return;
        }
        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    // ------------------------------------------------------------------- helpers
    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg);
        return m;
    }

    private static void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] data = Json.write(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    /** Finds the static/ folder whether you run from the project root, src/ or bin/. */
    private static Path findStaticDir() {
        for (String c : new String[]{"static", "../static", "../../static"}) {
            Path p = Paths.get(c).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) return p;
        }
        System.err.println("ERROR: 'static' folder not found. Open the project root folder in VS Code.");
        System.exit(1);
        return null;
    }
}
