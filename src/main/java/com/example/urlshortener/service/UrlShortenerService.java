package com.example.urlshortener.service;

import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.repository.Database;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class UrlShortenerService {
    private static final String BASE62_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final long START_COUNTER = 100_000_000L;
    private static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,30}$");
    private static final Set<String> RESERVED_WORDS = Set.of(
        "shorten", "analytics", "health", "favicon.ico", "static", "assets", "api"
    );

    private final Database database;
    private final AtomicLong counter;
    private final String serviceHost;
    private final int servicePort;
    private final Environment environment;

    public UrlShortenerService(
            Database database,
            @Value("${app.service.host:localhost}") String serviceHost,
            @Value("${server.port:8080}") int servicePort,
            Environment environment) {
        this.database = database;
        this.serviceHost = serviceHost;
        this.servicePort = servicePort;
        this.environment = environment;
        this.counter = new AtomicLong(initializeCounter());
    }

    private long initializeCounter() {
        long maxId = START_COUNTER;
        for (String code : database.getAllMappings().keySet()) {
            maxId = updateMaxIdIfValidCode(code, maxId);
        }
        return maxId == START_COUNTER ? START_COUNTER : maxId + 1;
    }

    private long updateMaxIdIfValidCode(String code, long currentMax) {
        UrlMapping mapping = database.get(code).orElse(null);
        if (mapping != null && !mapping.isCustom()) {
            return Math.max(currentMax, tryDecode(code));
        }
        return currentMax;
    }

    private long tryDecode(String code) {
        try {
            return decodeBase62(code);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    public static String encodeBase62(long num) {
        if (num < 0) throw new IllegalArgumentException("Number must be non-negative");
        if (num == 0) return String.valueOf(BASE62_ALPHABET.charAt(0));
        return buildBase62String(num);
    }

    private static String buildBase62String(long num) {
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
        return computeBase62Decode(str);
    }

    private static long computeBase62Decode(String str) {
        long num = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            int index = BASE62_ALPHABET.indexOf(c);
            if (index == -1) throw new IllegalArgumentException("Invalid character");
            num = num * 62 + index;
        }
        return num;
    }

    public boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) return false;
        try {
            URI uri = new URI(urlString);
            return hasValidScheme(uri) && hasValidHost(uri) && !isSelfReferencing(uri);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean hasValidScheme(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
    }

    private boolean hasValidHost(URI uri) {
        String host = uri.getHost();
        return host != null && !host.trim().isEmpty();
    }

    private boolean isSelfReferencing(URI uri) {
        int port = extractPort(uri);
        boolean hostMatches = isLocalHost(uri.getHost());
        
        int activePort = servicePort;
        if (activePort == 0) {
            String localPortStr = environment.getProperty("local.server.port");
            if (localPortStr != null) {
                activePort = Integer.parseInt(localPortStr);
            }
        }
        return hostMatches && port == activePort;
    }

    private int extractPort(URI uri) {
        int port = uri.getPort();
        if (port == -1) {
            return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
        }
        return port;
    }

    private boolean isLocalHost(String host) {
        if (host == null) return false;
        return host.equalsIgnoreCase("localhost") || 
               host.equalsIgnoreCase("127.0.0.1") || 
               host.equalsIgnoreCase(serviceHost);
    }

    public boolean isValidAlias(String alias) {
        if (alias == null) return false;
        if (RESERVED_WORDS.contains(alias.toLowerCase())) return false;
        return ALIAS_PATTERN.matcher(alias).matches();
    }

    public UrlMapping shortenUrl(String originalUrl, String customAlias) throws Exception {
        if (!isValidUrl(originalUrl)) {
            throw new IllegalArgumentException("Malformed or unsupported URL.");
        }
        if (customAlias != null && !customAlias.isEmpty()) {
            return handleCustomAlias(originalUrl, customAlias);
        }
        return handleRandomAlias(originalUrl);
    }

    private UrlMapping handleCustomAlias(String originalUrl, String customAlias) {
        if (!isValidAlias(customAlias)) {
            throw new IllegalArgumentException("Invalid alias.");
        }
        UrlMapping mapping = new UrlMapping(customAlias, originalUrl, true, System.currentTimeMillis());
        UrlMapping existing = database.putIfAbsent(mapping);
        if (existing == null) {
            // Insert succeeded — we won the race
            return mapping;
        }
        // Alias already exists — return it if same URL, otherwise throw
        if (existing.getOriginalUrl().equals(originalUrl)) {
            return existing;
        }
        throw new IllegalStateException("Alias already in use.");
    }

    private UrlMapping returnExistingOrThrow(String originalUrl, String alias) {
        UrlMapping existing = database.get(alias).orElse(null);
        if (existing != null && existing.getOriginalUrl().equals(originalUrl)) {
            return existing;
        }
        throw new IllegalStateException("Alias already in use.");
    }

    private UrlMapping handleRandomAlias(String originalUrl) {
        var existing = database.findByOriginalUrl(originalUrl);
        if (existing.isPresent()) return existing.get();

        String shortCode = generateUniqueShortCode();
        UrlMapping mapping = new UrlMapping(shortCode, originalUrl, false, System.currentTimeMillis());
        database.put(mapping);
        return mapping;
    }

    private String generateUniqueShortCode() {
        String shortCode;
        do {
            long id = counter.getAndIncrement();
            shortCode = encodeBase62(id);
        } while (database.exists(shortCode));
        return shortCode;
    }

    public UrlMapping getMapping(String shortCode) {
        return database.get(shortCode).orElse(null);
    }

    public UrlMapping resolve(String shortCode) {
        return getMapping(shortCode);
    }

    public long getCounterValue() {
        return counter.get();
    }
}
