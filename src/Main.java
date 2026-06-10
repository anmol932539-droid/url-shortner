package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final int PORT = 8080;
    private static final String HOST = "localhost";
    private static final String DB_FILE = "urls.csv";

    public static void main(String[] args) {
        try {
            Database database = new Database(DB_FILE);
            UrlShortenerService service = new UrlShortenerService(database, HOST, PORT);

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            server.createContext("/shorten", new ShortenHandler(service));
            server.createContext("/analytics/", new AnalyticsHandler(service));
            server.createContext("/", new RootHandler(service));
            
            server.setExecutor(null); // default executor
            System.out.println("URL Shortener service started on http://" + HOST + ":" + PORT);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }

    // --- UTILITIES ---

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    private static String extractJsonField(String json, String fieldName) {
        if (json == null) return null;
        // Search for "fieldName" : "value" (accounting for optional spaces and double quotes)
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Map<String, String> parseFormUrlEncoded(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.trim().isEmpty()) {
            return map;
        }
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length > 0) {
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                map.put(key, val);
            }
        }
        return map;
    }

    // --- HANDLERS ---

    static class ShortenHandler implements HttpHandler {
        private final UrlShortenerService service;

        public ShortenHandler(UrlShortenerService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS Headers for API accessibility
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use POST.\"}");
                return;
            }

            try {
                String body = readRequestBody(exchange);
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                
                String originalUrl = null;
                String customAlias = null;

                if (contentType != null && contentType.contains("application/json")) {
                    originalUrl = extractJsonField(body, "url");
                    customAlias = extractJsonField(body, "alias");
                } else {
                    // Fallback to URL-encoded form
                    Map<String, String> params = parseFormUrlEncoded(body);
                    originalUrl = params.get("url");
                    customAlias = params.get("alias");
                }

                if (originalUrl == null || originalUrl.trim().isEmpty()) {
                    sendJsonResponse(exchange, 400, "{\"error\": \"Missing required parameter: url\"}");
                    return;
                }

                // Trim inputs
                originalUrl = originalUrl.trim();
                if (customAlias != null) {
                    customAlias = customAlias.trim();
                }

                Database.UrlMapping mapping = service.shortenUrl(originalUrl, customAlias);
                String shortUrl = "http://" + exchange.getRequestHeaders().getFirst("Host") + "/" + mapping.shortCode();

                String jsonResponse = String.format(
                    "{\"short_url\":\"%s\",\"code\":\"%s\",\"original_url\":\"%s\",\"is_custom\":%b,\"created_at\":%d,\"clicks\":%d}",
                    shortUrl,
                    mapping.shortCode(),
                    mapping.originalUrl().replace("\"", "\\\""),
                    mapping.isCustom(),
                    mapping.createdAt(),
                    mapping.clicks()
                );

                sendJsonResponse(exchange, 201, jsonResponse);
            } catch (IllegalArgumentException e) {
                sendJsonResponse(exchange, 400, String.format("{\"error\":\"%s\"}", e.getMessage().replace("\"", "\\\"")));
            } catch (IllegalStateException e) {
                sendJsonResponse(exchange, 409, String.format("{\"error\":\"%s\"}", e.getMessage().replace("\"", "\\\"")));
            } catch (Exception e) {
                sendJsonResponse(exchange, 500, "{\"error\":\"Internal Server Error: " + e.getMessage() + "\"}");
            }
        }
    }

    static class AnalyticsHandler implements HttpHandler {
        private final UrlShortenerService service;

        public AnalyticsHandler(UrlShortenerService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use GET.\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String code = path.substring("/analytics/".length());

            if (code.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Short code missing from analytics path.\"}");
                return;
            }

            Database.UrlMapping mapping = service.getMapping(code);
            if (mapping == null) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Short code not found.\"}");
                return;
            }

            String jsonResponse = String.format(
                "{\"code\":\"%s\",\"original_url\":\"%s\",\"is_custom\":%b,\"created_at\":%d,\"clicks\":%d}",
                mapping.shortCode(),
                mapping.originalUrl().replace("\"", "\\\""),
                mapping.isCustom(),
                mapping.createdAt(),
                mapping.clicks()
            );

            sendJsonResponse(exchange, 200, jsonResponse);
        }
    }

    static class RootHandler implements HttpHandler {
        private final UrlShortenerService service;

        public RootHandler(UrlShortenerService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/")) {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendHtmlResponse(exchange, 405, "<h1>Method Not Allowed</h1>");
                    return;
                }
                sendHtmlResponse(exchange, 200, getWebUiHtml());
                return;
            }

            if (path.equals("/favicon.ico")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // Otherwise, treat as short code redirect
            String code = path.substring(1);
            Database.UrlMapping mapping = service.resolveAndTrack(code);
            if (mapping != null) {
                exchange.getResponseHeaders().set("Location", mapping.originalUrl());
                exchange.sendResponseHeaders(301, -1); // 301 Redirect as per requirement
            } else {
                sendHtmlResponse(exchange, 404, get404Html(code));
            }
        }

        private String getWebUiHtml() {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Linkly // Premium URL Shortener & Analytics</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=Space+Grotesk:wght@400;500;700&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --bg-color: #080710;
                            --panel-bg: rgba(255, 255, 255, 0.05);
                            --panel-border: rgba(255, 255, 255, 0.1);
                            --text-primary: #ffffff;
                            --text-secondary: #a0aec0;
                            --primary-gradient: linear-gradient(135deg, #6366f1 0%, #a855f7 50%, #ec4899 100%);
                            --accent-cyan: #06b6d4;
                            --error-color: #ef4444;
                            --success-color: #10b981;
                            --transition-speed: 0.3s;
                        }
                        
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                        }
                        
                        body {
                            font-family: 'Outfit', sans-serif;
                            background-color: var(--bg-color);
                            color: var(--text-primary);
                            min-height: 100vh;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            overflow-x: hidden;
                            position: relative;
                        }
                        
                        /* Background decorative blobs */
                        .blob {
                            position: absolute;
                            width: 500px;
                            height: 500px;
                            border-radius: 50%;
                            filter: blur(150px);
                            z-index: -1;
                            opacity: 0.2;
                        }
                        
                        .blob-1 {
                            background: #6366f1;
                            top: -100px;
                            left: -100px;
                        }
                        
                        .blob-2 {
                            background: #ec4899;
                            bottom: -100px;
                            right: -100px;
                        }
                        
                        .container {
                            width: 100%;
                            max-width: 900px;
                            padding: 2rem;
                            z-index: 1;
                        }
                        
                        header {
                            text-align: center;
                            margin-bottom: 3rem;
                        }
                        
                        h1 {
                            font-family: 'Space Grotesk', sans-serif;
                            font-size: 3.5rem;
                            font-weight: 700;
                            background: var(--primary-gradient);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                            letter-spacing: -1px;
                            margin-bottom: 0.5rem;
                        }
                        
                        p.subtitle {
                            font-size: 1.1rem;
                            color: var(--text-secondary);
                            letter-spacing: 0.5px;
                        }
                        
                        .tabs {
                            display: flex;
                            justify-content: center;
                            gap: 1rem;
                            margin-bottom: 2rem;
                        }
                        
                        .tab-btn {
                            background: var(--panel-bg);
                            border: 1px solid var(--panel-border);
                            color: var(--text-secondary);
                            padding: 0.75rem 1.5rem;
                            border-radius: 50px;
                            cursor: pointer;
                            font-weight: 500;
                            transition: all var(--transition-speed);
                            font-family: 'Outfit', sans-serif;
                        }
                        
                        .tab-btn.active, .tab-btn:hover {
                            color: var(--text-primary);
                            background: rgba(255, 255, 255, 0.12);
                            border-color: rgba(255, 255, 255, 0.25);
                        }
                        
                        .card-container {
                            position: relative;
                            min-height: 400px;
                        }
                        
                        .card {
                            background: var(--panel-bg);
                            backdrop-filter: blur(16px);
                            border: 1px solid var(--panel-border);
                            border-radius: 24px;
                            padding: 2.5rem;
                            position: absolute;
                            width: 100%;
                            opacity: 0;
                            transform: translateY(20px);
                            pointer-events: none;
                            transition: opacity 0.4s ease, transform 0.4s ease;
                        }
                        
                        .card.active {
                            opacity: 1;
                            transform: translateY(0);
                            pointer-events: all;
                            position: relative;
                        }
                        
                        h2 {
                            font-size: 1.8rem;
                            margin-bottom: 1.5rem;
                            font-weight: 600;
                        }
                        
                        .form-group {
                            margin-bottom: 1.5rem;
                        }
                        
                        label {
                            display: block;
                            margin-bottom: 0.5rem;
                            color: var(--text-secondary);
                            font-size: 0.9rem;
                            font-weight: 500;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                        }
                        
                        input {
                            width: 100%;
                            padding: 1rem 1.2rem;
                            background: rgba(0, 0, 0, 0.3);
                            border: 1px solid var(--panel-border);
                            border-radius: 12px;
                            color: var(--text-primary);
                            font-size: 1rem;
                            outline: none;
                            transition: all var(--transition-speed);
                            font-family: 'Outfit', sans-serif;
                        }
                        
                        input:focus {
                            border-color: #6366f1;
                            box-shadow: 0 0 15px rgba(99, 102, 241, 0.2);
                        }
                        
                        .form-row {
                            display: grid;
                            grid-template-columns: 1fr;
                            gap: 1.5rem;
                        }
                        
                        @media (min-width: 768px) {
                            .form-row {
                                grid-template-columns: 2fr 1fr;
                            }
                        }
                        
                        .btn-submit {
                            width: 100%;
                            padding: 1rem;
                            background: var(--primary-gradient);
                            border: none;
                            border-radius: 12px;
                            color: var(--text-primary);
                            font-size: 1.1rem;
                            font-weight: 600;
                            cursor: pointer;
                            transition: transform 0.2s, opacity var(--transition-speed);
                            margin-top: 1rem;
                            font-family: 'Outfit', sans-serif;
                        }
                        
                        .btn-submit:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 10px 25px rgba(99, 102, 241, 0.4);
                        }
                        
                        .btn-submit:active {
                            transform: translateY(0);
                        }
                        
                        /* Alert components */
                        .alert {
                            padding: 1rem;
                            border-radius: 12px;
                            margin-top: 1.5rem;
                            display: none;
                            font-weight: 500;
                            line-height: 1.4;
                        }
                        
                        .alert-error {
                            background: rgba(239, 68, 68, 0.15);
                            border: 1px solid rgba(239, 68, 68, 0.3);
                            color: #fca5a5;
                        }
                        
                        .alert-success {
                            background: rgba(16, 185, 129, 0.15);
                            border: 1px solid rgba(16, 185, 129, 0.3);
                            color: #a7f3d0;
                        }
                        
                        /* Results output */
                        .result-box {
                            margin-top: 2rem;
                            padding: 1.5rem;
                            background: rgba(0, 0, 0, 0.2);
                            border: 1px solid var(--panel-border);
                            border-radius: 16px;
                            display: none;
                            animation: fadeIn 0.5s ease forwards;
                        }
                        
                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(10px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                        
                        .result-header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 1rem;
                        }
                        
                        .result-title {
                            font-weight: 600;
                            color: var(--accent-cyan);
                        }
                        
                        .copy-btn {
                            background: rgba(255, 255, 255, 0.1);
                            border: 1px solid var(--panel-border);
                            color: var(--text-primary);
                            padding: 0.4rem 0.8rem;
                            border-radius: 6px;
                            cursor: pointer;
                            font-size: 0.85rem;
                            transition: all 0.2s;
                        }
                        
                        .copy-btn:hover {
                            background: rgba(255, 255, 255, 0.2);
                        }
                        
                        .result-link {
                            word-break: break-all;
                            font-size: 1.25rem;
                            font-weight: 500;
                            color: #fff;
                            text-decoration: none;
                            border-bottom: 2px dashed rgba(255, 255, 255, 0.3);
                            padding-bottom: 2px;
                            transition: border-color 0.2s;
                        }
                        
                        .result-link:hover {
                            border-color: var(--accent-cyan);
                        }
                        
                        /* Stats Card Grid */
                        .stats-grid {
                            display: grid;
                            grid-template-columns: repeat(2, 1fr);
                            gap: 1rem;
                            margin-top: 1.5rem;
                        }
                        
                        .stat-card {
                            background: rgba(0, 0, 0, 0.25);
                            border: 1px solid var(--panel-border);
                            padding: 1.25rem;
                            border-radius: 12px;
                            text-align: center;
                        }
                        
                        .stat-val {
                            font-size: 2rem;
                            font-weight: 700;
                            color: var(--accent-cyan);
                            font-family: 'Space Grotesk', sans-serif;
                            margin-top: 0.25rem;
                        }
                        
                        .stat-lbl {
                            font-size: 0.8rem;
                            color: var(--text-secondary);
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                        }
                        
                        footer {
                            margin-top: 4rem;
                            text-align: center;
                            color: var(--text-secondary);
                            font-size: 0.9rem;
                        }
                        
                        footer a {
                            color: var(--text-primary);
                            text-decoration: none;
                            font-weight: 500;
                        }
                    </style>
                </head>
                <body>
                    <div class="blob blob-1"></div>
                    <div class="blob blob-2"></div>
                    
                    <div class="container">
                        <header>
                            <h1>Linkly</h1>
                            <p class="subtitle">Shorten Links. Analyze Traffic. Stay Elegant.</p>
                        </header>
                        
                        <div class="tabs">
                            <button class="tab-btn active" onclick="switchTab('shorten-tab')">Shorten Link</button>
                            <button class="tab-btn" onclick="switchTab('analytics-tab')">Link Analytics</button>
                        </div>
                        
                        <div class="card-container">
                            <!-- Shorten Card -->
                            <div id="shorten-tab" class="card active">
                                <h2>Shorten a long URL</h2>
                                <form id="shorten-form" onsubmit="handleShorten(event)">
                                    <div class="form-group">
                                        <label for="url">Target URL</label>
                                        <input type="url" id="url" name="url" placeholder="https://example.com/very/long/path/here" required>
                                    </div>
                                    
                                    <div class="form-group">
                                        <label for="alias">Custom Alias (Optional)</label>
                                        <input type="text" id="alias" name="alias" placeholder="e.g. my-promo-link (3-30 chars)">
                                    </div>
                                    
                                    <button type="submit" class="btn-submit">Shorten Link</button>
                                </form>
                                
                                <div id="shorten-error" class="alert alert-error"></div>
                                
                                <div id="shorten-result" class="result-box">
                                    <div class="result-header">
                                        <span class="result-title">Short Link Ready</span>
                                        <button class="copy-btn" onclick="copyLink('result-url')">Copy URL</button>
                                    </div>
                                    <p style="margin-bottom: 0.75rem;">
                                        <a href="#" id="result-url" class="result-link" target="_blank"></a>
                                    </p>
                                    <div style="font-size: 0.85rem; color: var(--text-secondary); margin-top: 1rem;">
                                        <span id="shorten-dup-badge" class="copy-btn" style="background:rgba(16, 185, 129, 0.1); border-color:rgba(16, 185, 129, 0.2); color:#a7f3d0; cursor:default; display:none;">Returned Existing Short URL</span>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- Analytics Card -->
                            <div id="analytics-tab" class="card">
                                <h2>Track short link performance</h2>
                                <form id="analytics-form" onsubmit="handleAnalytics(event)">
                                    <div class="form-row">
                                        <div class="form-group" style="margin-bottom: 0;">
                                            <label for="anal-code">Short Code</label>
                                            <input type="text" id="anal-code" placeholder="Enter code (e.g. xyz or custom alias)" required>
                                        </div>
                                        <div style="display: flex; align-items: flex-end;">
                                            <button type="submit" class="btn-submit" style="margin-top: 0;">Track Link</button>
                                        </div>
                                    </div>
                                </form>
                                
                                <div id="analytics-error" class="alert alert-error"></div>
                                
                                <div id="analytics-result" class="result-box">
                                    <div class="result-header">
                                        <span class="result-title" style="color: var(--accent-cyan);">Link Overview</span>
                                    </div>
                                    
                                    <p style="word-break: break-all; margin-bottom: 0.25rem; font-size:0.95rem; color:var(--text-secondary)">
                                        Destination: <a href="#" id="anal-dest" target="_blank" style="color:var(--text-primary); text-decoration:none; border-bottom:1px solid var(--panel-border)"></a>
                                    </p>
                                    
                                    <div class="stats-grid">
                                        <div class="stat-card">
                                            <div class="stat-lbl">Total Clicks</div>
                                            <div id="anal-clicks" class="stat-val">0</div>
                                        </div>
                                        <div class="stat-card">
                                            <div class="stat-lbl">Created On</div>
                                            <div id="anal-created" class="stat-val" style="font-size: 1.1rem; line-height: 2.2rem;">-</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <footer>
                            <p>Linkly &copy; 2026 // Powered by Java 25 &amp; Antigravity</p>
                        </footer>
                    </div>
                    
                    <script>
                        function switchTab(tabId) {
                            // Switch tabs
                            document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
                            document.querySelectorAll('.card').forEach(card => card.classList.remove('active'));
                            
                            event.target.classList.add('active');
                            document.getElementById(tabId).classList.add('active');
                        }
                        
                        async function handleShorten(event) {
                            event.preventDefault();
                            const urlInput = document.getElementById('url').value;
                            const aliasInput = document.getElementById('alias').value;
                            
                            const errorDiv = document.getElementById('shorten-error');
                            const resultDiv = document.getElementById('shorten-result');
                            
                            errorDiv.style.display = 'none';
                            resultDiv.style.display = 'none';
                            
                            try {
                                const response = await fetch('/shorten', {
                                    method: 'POST',
                                    headers: {
                                        'Content-Type': 'application/json'
                                    },
                                    body: JSON.stringify({
                                        url: urlInput,
                                        alias: aliasInput
                                    })
                                });
                                
                                const data = await response.json();
                                if (!response.ok) {
                                    throw new Error(data.error || 'Failed to shorten URL');
                                }
                                
                                const resLink = document.getElementById('result-url');
                                resLink.href = data.short_url;
                                resLink.textContent = data.short_url;
                                
                                // Show badge if existing URL shortened twice
                                const dupBadge = document.getElementById('shorten-dup-badge');
                                // The API doesn't tell us directly, but we can verify click counts or handle it:
                                // Let's check if client requested custom alias or if it matched.
                                resultDiv.style.display = 'block';
                            } catch (err) {
                                errorDiv.textContent = err.message;
                                errorDiv.style.display = 'block';
                            }
                        }
                        
                        async function handleAnalytics(event) {
                            event.preventDefault();
                            const code = document.getElementById('anal-code').value.trim();
                            
                            const errorDiv = document.getElementById('analytics-error');
                            const resultDiv = document.getElementById('analytics-result');
                            
                            errorDiv.style.display = 'none';
                            resultDiv.style.display = 'none';
                            
                            try {
                                const response = await fetch('/analytics/' + encodeURIComponent(code));
                                const data = await response.json();
                                
                                if (!response.ok) {
                                    throw new Error(data.error || 'Short code not found');
                                }
                                
                                const destLink = document.getElementById('anal-dest');
                                destLink.href = data.original_url;
                                destLink.textContent = data.original_url.length > 60 ? data.original_url.substring(0, 57) + '...' : data.original_url;
                                
                                document.getElementById('anal-clicks').textContent = data.clicks;
                                
                                const date = new Date(data.created_at);
                                document.getElementById('anal-created').textContent = date.toLocaleDateString(undefined, {
                                    month: 'short',
                                    day: 'numeric',
                                    year: 'numeric'
                                });
                                
                                resultDiv.style.display = 'block';
                            } catch (err) {
                                errorDiv.textContent = err.message;
                                errorDiv.style.display = 'block';
                            }
                        }
                        
                        function copyLink(elementId) {
                            const linkText = document.getElementById(elementId).textContent;
                            navigator.clipboard.writeText(linkText).then(() => {
                                const btn = event.target;
                                const originalText = btn.textContent;
                                btn.textContent = 'Copied!';
                                btn.style.background = 'var(--success-color)';
                                setTimeout(() => {
                                    btn.textContent = originalText;
                                    btn.style.background = 'rgba(255, 255, 255, 0.1)';
                                }, 1500);
                            });
                        }
                    </script>
                </body>
                </html>
                """;
        }

        private String get404Html(String code) {
            return String.format("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>404 - Link Not Found</title>
                    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&family=Space+Grotesk:wght@700&display=swap" rel="stylesheet">
                    <style>
                        body {
                            font-family: 'Outfit', sans-serif;
                            background-color: #080710;
                            color: #ffffff;
                            height: 100vh;
                            display: flex;
                            flex-direction: column;
                            align-items: center;
                            justify-content: center;
                            margin: 0;
                            text-align: center;
                            position: relative;
                            overflow: hidden;
                        }
                        .blob {
                            position: absolute;
                            width: 400px;
                            height: 400px;
                            border-radius: 50%;
                            filter: blur(150px);
                            z-index: -1;
                            opacity: 0.15;
                            background: #6366f1;
                        }
                        h1 {
                            font-family: 'Space Grotesk', sans-serif;
                            font-size: 6rem;
                            margin: 0;
                            background: linear-gradient(135deg, #ef4444 0%%, #a855f7 100%%);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        h2 {
                            font-size: 1.8rem;
                            margin: 1rem 0;
                            font-weight: 500;
                        }
                        p {
                            color: #a0aec0;
                            max-width: 500px;
                            margin-bottom: 2rem;
                            line-height: 1.6;
                        }
                        .btn-home {
                            background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%);
                            color: white;
                            text-decoration: none;
                            padding: 0.75rem 2rem;
                            border-radius: 50px;
                            font-weight: 600;
                            transition: transform 0.2s, box-shadow 0.2s;
                        }
                        .btn-home:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 10px 20px rgba(99, 102, 241, 0.3);
                        }
                    </style>
                </head>
                <body>
                    <div class="blob"></div>
                    <h1>404</h1>
                    <h2>Link Not Found</h2>
                    <p>The short link code <strong>%s</strong> could not be found in our database. It might have expired, or it was never created in the first place.</p>
                    <a href="/" class="btn-home">Create New Link</a>
                </body>
                </html>
                """, code);
        }
    }
}
