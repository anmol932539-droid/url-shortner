package src;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class UrlShortenerService {
    private static final String BASE62_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final long START_COUNTER = 100_000_000L; // 5 characters in Base62
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
    private static final Set<String> RESERVED_WORDS = Set.of(
        "shorten", "analytics", "health", "favicon.ico", "static", "assets", "api"
    );

    private final Database database;
    private final AtomicLong counter;
    private final String serviceHost;
    private final int servicePort;

    public UrlShortenerService(Database database, String serviceHost, int servicePort) {
        this.database = database;
        this.serviceHost = serviceHost;
        this.servicePort = servicePort;
        this.counter = new AtomicLong(initializeCounter());
    }

    private long initializeCounter() {
        long maxId = START_COUNTER;
        for (String code : database.getAllMappings().keySet()) {
            Database.UrlMapping mapping = database.get(code).orElse(null);
            if (mapping != null && !mapping.isCustom()) {
                try {
                    long id = decodeBase62(code);
                    if (id > maxId) {
                        maxId = id;
                    }
                } catch (IllegalArgumentException ignored) {
                    // If it can't be decoded, treat it as custom or skip
                }
            }
        }
        // If there were existing mappings, we start at maxId + 1
        return maxId == START_COUNTER ? START_COUNTER : maxId + 1;
    }

    public static String encodeBase62(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("Number must be non-negative");
        }
        if (num == 0) {
            return String.valueOf(BASE62_ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62_ALPHABET.charAt((int) (num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    public static long decodeBase62(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("String cannot be null or empty");
        }
        long num = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = BASE62_ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            num = num * 62 + index;
        }
        return num;
    }

    public boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            return false;
        }
        try {
            URI uri = new URI(urlString);
            // Must have scheme (http/https) and host
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                return false;
            }

            // Prevent self-shortening loop
            // E.g., if the user tries to shorten http://localhost:8080/xyz
            if (isSelfReferencing(uri)) {
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isSelfReferencing(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        
        // Resolve default ports
        if (port == -1) {
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                port = 80;
            } else if ("https".equalsIgnoreCase(uri.getScheme())) {
                port = 443;
            }
        }

        int localPort = servicePort;
        // Check if host matches localhost, 127.0.0.1, or serviceHost
        boolean hostMatches = host.equalsIgnoreCase("localhost") || 
                             host.equalsIgnoreCase("127.0.0.1") || 
                             host.equalsIgnoreCase(serviceHost);
        
        return hostMatches && port == localPort;
    }

    public boolean isValidAlias(String alias) {
        if (alias == null) {
            return false;
        }
        if (RESERVED_WORDS.contains(alias.toLowerCase())) {
            return false;
        }
        return ALIAS_PATTERN.matcher(alias).matches();
    }

    public Database.UrlMapping shortenUrl(String originalUrl, String customAlias) throws Exception {
        if (!isValidUrl(originalUrl)) {
            throw new IllegalArgumentException("Malformed or unsupported URL. Only http/https are allowed, and self-referencing URLs are forbidden.");
        }

        if (customAlias != null && !customAlias.isEmpty()) {
            if (!isValidAlias(customAlias)) {
                throw new IllegalArgumentException("Invalid alias. Must be alphanumeric/hyphen/underscore (3-30 chars) and not a reserved word.");
            }
            // Check if alias is already in use
            if (database.exists(customAlias)) {
                throw new IllegalStateException("Alias already in use.");
            }
            
            // Create custom alias mapping (always write a new mapping)
            Database.UrlMapping mapping = new Database.UrlMapping(
                customAlias, originalUrl, true, System.currentTimeMillis(), 0
            );
            database.put(mapping);
            return mapping;
        } else {
            // Check for existing non-custom mapping (Duplicate URL Handling)
            var existing = database.findByOriginalUrl(originalUrl);
            if (existing.isPresent()) {
                return existing.get();
            }

            // Generate new short code
            String shortCode;
            do {
                long id = counter.getAndIncrement();
                shortCode = encodeBase62(id);
            } while (database.exists(shortCode)); // Safety check to prevent collisions with custom aliases

            Database.UrlMapping mapping = new Database.UrlMapping(
                shortCode, originalUrl, false, System.currentTimeMillis(), 0
            );
            database.put(mapping);
            return mapping;
        }
    }

    public Database.UrlMapping getMapping(String shortCode) {
        return database.get(shortCode).orElse(null);
    }

    public Database.UrlMapping resolveAndTrack(String shortCode) {
        Database.UrlMapping mapping = getMapping(shortCode);
        if (mapping != null) {
            database.incrementClicks(shortCode);
            return getMapping(shortCode); // Return updated mapping
        }
        return null;
    }

    public long getCounterValue() {
        return counter.get();
    }
}
