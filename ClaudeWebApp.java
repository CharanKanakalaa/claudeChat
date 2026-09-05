import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ClaudeWebApp.java
 *
 * 
 *
 * A minimal web app: a Java backend (using the JDK's built-in HttpServer,
 * no external dependencies) serves a single HTML page and an API endpoint.
 * The page lets you type a message and/or upload a text file; the backend
 * forwards it to the Claude API and returns the answer.
 *
 * HOW TO RUN:
 *   1. Set your API key:
 *        export ANTHROPIC_API_KEY=your_key_here
 *   2. Compile (run this from the claude-java-starter folder, so the
 *      "public" folder is a sibling of the .java file):
 *        javac ClaudeWebApp.java
 *   3. Run:
 *        java ClaudeWebApp
 *   4. Open your browser to:
 *        http://localhost:8080
 */
public class ClaudeWebApp {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-5-20250929"; // change if needed
    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("ERROR: Please set the ANTHROPIC_API_KEY environment variable first.");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/chat", new ChatHandler(apiKey));
        server.setExecutor(null); // uses a simple default executor, fine for local/learning use
        server.start();

        System.out.println("Server running at http://localhost:" + PORT);
        System.out.println("Press Ctrl+C to stop.");
    }

    /** Serves the single page at "/". */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlainText(exchange, 405, "Method not allowed");
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(Path.of("public", "index.html"));
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                sendPlainText(exchange, 500,
                        "Could not load public/index.html. Make sure you run this "
                        + "program from the claude-java-starter folder. (" + e.getMessage() + ")");
            }
        }
    }

    /** Handles POST /api/chat — receives {message, fileContent}, calls Claude, returns {reply}. */
    static class ChatHandler implements HttpHandler {
        private final String apiKey;

        ChatHandler(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public void handle(HttpExchange exchange) {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlainText(exchange, 405, "Method not allowed");
                    return;
                }

                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String message = extractJsonStringField(requestBody, "message");
                String fileContent = extractJsonStringField(requestBody, "fileContent");

                if (message == null) message = "";
                String prompt = (fileContent != null && !fileContent.isBlank())
                        ? message + "\n\n---\n\n" + fileContent
                        : message;

                if (prompt.isBlank()) {
                    sendJson(exchange, 400, "{\"reply\":\"Please type a message or attach a file.\"}");
                    return;
                }

                String reply;
                try {
                    reply = askClaude(prompt, apiKey);
                } catch (Exception e) {
                    reply = "Error calling Claude: " + e.getMessage();
                }

                String jsonResponse = "{\"reply\":\"" + escapeJson(reply) + "\"}";
                sendJson(exchange, 200, jsonResponse);

            } catch (Exception e) {
                try {
                    sendJson(exchange, 500, "{\"reply\":\"Server error: " + escapeJson(e.getMessage()) + "\"}");
                } catch (Exception ignored) {
                    // nothing more we can do
                }
            }
        }
    }

    // ---------- Claude API call (same pattern as the earlier CLI tools) ----------

    private static String askClaude(String prompt, String apiKey) throws Exception {
        String escapedPrompt = escapeJson(prompt);

        String jsonBody = """
            {
              "model": "%s",
              "max_tokens": 1024,
              "messages": [
                {"role": "user", "content": "%s"}
              ]
            }
            """.formatted(MODEL, escapedPrompt);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "API error (" + response.statusCode() + "): " + response.body();
        }

        return extractJsonStringField(response.body(), "text");
    }

    // ---------- Small dependency-free JSON helpers ----------
    // (For anything beyond this learning project, use a real JSON library
    // like org.json or Jackson instead of manual string parsing.)

    /** Finds "fieldName":"value" in a JSON string and returns the unescaped value, or null if not found. */
    private static String extractJsonStringField(String json, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = json.indexOf(marker);
        if (start == -1) return null;
        start += marker.length();

        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    default -> result.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                break;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    // ---------- Small HTTP response helpers ----------

    private static void sendPlainText(HttpExchange exchange, int statusCode, String text) throws java.io.IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws java.io.IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
