package com.example.urlshortener;

import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.repository.Database;
import com.example.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "app.db.file=test_urls.csv",
    "app.service.host=localhost",
    "server.port=8080"
})
class UrlShortenerApplicationTests {

    @Autowired
    private Database database;

    @Autowired
    private UrlShortenerService service;

    @BeforeEach
    @AfterEach
    void cleanup() {
        database.clear();
    }

    @Test
    void testBase62() {
        // Test base values
        assertEquals("a", UrlShortenerService.encodeBase62(0));
        assertEquals(0L, UrlShortenerService.decodeBase62("a"));

        // Roundtrip testing
        long[] testValues = {0, 1, 61, 62, 12345, 999999999L, 100000000000L};
        for (long val : testValues) {
            String encoded = UrlShortenerService.encodeBase62(val);
            long decoded = UrlShortenerService.decodeBase62(encoded);
            assertEquals(val, decoded);
        }

        // Invalid decode validation
        assertThrows(IllegalArgumentException.class, () -> UrlShortenerService.decodeBase62("a#b"));
    }

    @Test
    void testUrlValidation() {
        // Valid URLs
        assertTrue(service.isValidUrl("http://google.com"));
        assertTrue(service.isValidUrl("https://github.com/google/gemini-client"));
        assertTrue(service.isValidUrl("http://localhost:3000/dashboard"));

        // Invalid URLs
        assertFalse(service.isValidUrl("ftp://ftp.example.com"));
        assertFalse(service.isValidUrl("google.com"));
        assertFalse(service.isValidUrl("http:///no-host"));
        assertFalse(service.isValidUrl(null));
        assertFalse(service.isValidUrl("    "));

        // Recursion prevention: self-referencing links on the active port (localhost:8080)
        assertFalse(service.isValidUrl("http://localhost:8080/xyz"));
        assertFalse(service.isValidUrl("http://127.0.0.1:8080/xyz"));
        
        // Self-referencing links but on a DIFFERENT port are allowed
        assertTrue(service.isValidUrl("http://localhost:9000/xyz"));
    }

    @Test
    void testAliasValidation() {
        // Valid aliases
        assertTrue(service.isValidAlias("promo2026"));
        assertTrue(service.isValidAlias("my_alias-1"));
        assertTrue(service.isValidAlias("abc"));

        // Invalid aliases
        assertFalse(service.isValidAlias("ab")); // too short
        assertFalse(service.isValidAlias("thisaliasiswaytoolongtofitinourrequirements")); // too long
        assertFalse(service.isValidAlias("my alias")); // spaces
        assertFalse(service.isValidAlias("my@alias")); // special char
        
        // Reserved words
        assertFalse(service.isValidAlias("shorten"));
        assertFalse(service.isValidAlias("analytics"));
        assertFalse(service.isValidAlias("health"));
    }

    @Test
    void testDatabaseOperations() {
        UrlMapping m1 = new UrlMapping("xyz", "https://google.com", false, System.currentTimeMillis(), 0);
        database.put(m1);

        assertTrue(database.exists("xyz"));
        var fetched = database.get("xyz");
        assertTrue(fetched.isPresent());
        assertEquals("https://google.com", fetched.get().originalUrl());
        assertEquals(0, fetched.get().clicks());

        // Increment clicks
        database.incrementClicks("xyz");
        var updated = database.get("xyz");
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().clicks());

    }

    @Test
    void testDuplicateUrlHandling() throws Exception {
        // Shorten a URL the first time
        UrlMapping m1 = service.shortenUrl("https://example.com", null);
        String code1 = m1.shortCode();

        // Shorten the SAME URL without custom alias
        UrlMapping m2 = service.shortenUrl("https://example.com", null);
        String code2 = m2.shortCode();

        // Should return the exact same mapping/code (idempotent duplicate URL handling)
        assertEquals(code1, code2);
        assertEquals(m1.createdAt(), m2.createdAt());

        // Shorten the SAME URL but WITH a custom alias
        UrlMapping m3 = service.shortenUrl("https://example.com", "my-custom-example");
        String code3 = m3.shortCode();

        // Custom alias should be honored as a distinct mapping
        assertEquals("my-custom-example", code3);
        assertNotEquals(code1, code3);
    }

    @Test
    void testConcurrency() throws Exception {
        int numThreads = 10;
        int operationsPerThread = 50;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        Set<String> generatedCodes = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String url = "https://example.com/thread-" + threadId + "-op-" + j;
                        UrlMapping mapping = service.shortenUrl(url, null);
                        generatedCodes.add(mapping.shortCode());
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

        assertTrue(finished);
        assertTrue(errors.isEmpty());
        // 10 threads * 50 ops = 500 unique URLs.
        assertEquals(500, generatedCodes.size());
        assertEquals(500, database.getAllMappings().size());

    }
}
