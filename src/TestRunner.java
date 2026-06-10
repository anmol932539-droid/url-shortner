package src;

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

public class TestRunner {
    private static final String TEST_DB_FILE = "test_urls.csv";
    private static int testsRun = 0;
    private static int testsPassed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("==================================================");
        System.out.println("      Starting URL Shortener Test Suite           ");
        System.out.println("==================================================");

        try {
            // Cleanup any leftovers
            Files.deleteIfExists(Path.of(TEST_DB_FILE));

            runTest("Base62 Encoding and Decoding", TestRunner::testBase62);
            runTest("URL Validation Logic", TestRunner::testUrlValidation);
            runTest("Custom Alias Validation", TestRunner::testAliasValidation);
            runTest("Database CRUD and Persistence", TestRunner::testDatabaseOperations);
            runTest("Duplicate URL Shortening behavior", TestRunner::testDuplicateUrlHandling);
            runTest("Concurrency and Thread Safety", TestRunner::testConcurrency);

            System.out.println("==================================================");
            System.out.printf("Test Execution Finished: %d/%d Passed.\n", testsPassed, testsRun);
            System.out.println("==================================================");

            if (testsPassed < testsRun) {
                System.exit(1);
            }
        } finally {
            // Final cleanup
            try {
                Files.deleteIfExists(Path.of(TEST_DB_FILE));
            } catch (IOException ignored) {}
        }
    }

    private static void runTest(String name, TestRunnable test) {
        testsRun++;
        System.out.print("Running: " + name + "... ");
        try {
            test.run();
            testsPassed++;
            System.out.println("PASSED");
        } catch (Throwable e) {
            System.out.println("FAILED");
            e.printStackTrace();
        }
    }

    interface TestRunnable {
        void run() throws Exception;
    }

    // --- TESTS ---

    private static void testBase62() {
        // Test base values
        assertEqual("a", UrlShortenerService.encodeBase62(0));
        assertEqual(0L, UrlShortenerService.decodeBase62("a"));

        // Roundtrip testing
        long[] testValues = {0, 1, 61, 62, 12345, 999999999L, 100000000000L};
        for (long val : testValues) {
            String encoded = UrlShortenerService.encodeBase62(val);
            long decoded = UrlShortenerService.decodeBase62(encoded);
            assertEqual(val, decoded);
        }

        // Invalid decode validation
        try {
            UrlShortenerService.decodeBase62("a#b");
            throw new AssertionError("Should have failed on invalid base62 character");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    private static void testUrlValidation() {
        Database db = new Database(TEST_DB_FILE);
        UrlShortenerService service = new UrlShortenerService(db, "localhost", 8080);

        // Valid URLs
        assertTrue(service.isValidUrl("http://google.com"));
        assertTrue(service.isValidUrl("https://github.com/google/gemini-client"));
        assertTrue(service.isValidUrl("http://localhost:3000/dashboard"));

        // Invalid URLs
        assertFalse(service.isValidUrl("ftp://ftp.example.com")); // wrong scheme
        assertFalse(service.isValidUrl("google.com")); // missing scheme
        assertFalse(service.isValidUrl("http:///no-host")); // missing host
        assertFalse(service.isValidUrl(null));
        assertFalse(service.isValidUrl("    "));

        // Recursion prevention: self-referencing links on the active port (localhost:8080)
        assertFalse(service.isValidUrl("http://localhost:8080/xyz"));
        assertFalse(service.isValidUrl("http://127.0.0.1:8080/xyz"));
        
        // Self-referencing links but on a DIFFERENT port are allowed
        assertTrue(service.isValidUrl("http://localhost:9000/xyz"));
    }

    private static void testAliasValidation() {
        Database db = new Database(TEST_DB_FILE);
        UrlShortenerService service = new UrlShortenerService(db, "localhost", 8080);

        // Valid aliases
        assertTrue(service.isValidAlias("promo2026"));
        assertTrue(service.isValidAlias("my_alias-1"));
        assertTrue(service.isValidAlias("abc"));

        // Invalid aliases
        assertFalse(service.isValidAlias("ab")); // too short (min 3)
        assertFalse(service.isValidAlias("thisaliasiswaytoolongtofitinourrequirements")); // too long (max 30)
        assertFalse(service.isValidAlias("my alias")); // spaces
        assertFalse(service.isValidAlias("my@alias")); // special char
        
        // Reserved words
        assertFalse(service.isValidAlias("shorten"));
        assertFalse(service.isValidAlias("analytics"));
        assertFalse(service.isValidAlias("health"));
    }

    private static void testDatabaseOperations() throws Exception {
        Database db = new Database(TEST_DB_FILE);
        db.clear();

        Database.UrlMapping m1 = new Database.UrlMapping("xyz", "https://google.com", false, System.currentTimeMillis(), 0);
        db.put(m1);

        assertTrue(db.exists("xyz"));
        var fetched = db.get("xyz");
        assertTrue(fetched.isPresent());
        assertEqual("https://google.com", fetched.get().originalUrl());
        assertEqual(0, fetched.get().clicks());

        // Increment clicks
        db.incrementClicks("xyz");
        var updated = db.get("xyz");
        assertTrue(updated.isPresent());
        assertEqual(1, updated.get().clicks());

        // Test persistence reload
        Database db2 = new Database(TEST_DB_FILE);
        assertTrue(db2.exists("xyz"));
        var reloaded = db2.get("xyz").get();
        assertEqual("https://google.com", reloaded.originalUrl());
        assertEqual(1, reloaded.clicks());

        db2.clear();
    }

    private static void testDuplicateUrlHandling() throws Exception {
        Database db = new Database(TEST_DB_FILE);
        db.clear();
        UrlShortenerService service = new UrlShortenerService(db, "localhost", 8080);

        // Shorten a URL the first time
        Database.UrlMapping m1 = service.shortenUrl("https://example.com", null);
        String code1 = m1.shortCode();

        // Shorten the SAME URL without custom alias
        Database.UrlMapping m2 = service.shortenUrl("https://example.com", null);
        String code2 = m2.shortCode();

        // Should return the exact same mapping/code (idempotent duplicate URL handling)
        assertEqual(code1, code2);
        assertEqual(m1.createdAt(), m2.createdAt());

        // Shorten the SAME URL but WITH a custom alias
        Database.UrlMapping m3 = service.shortenUrl("https://example.com", "my-custom-example");
        String code3 = m3.shortCode();

        // Custom alias should be honored as a distinct mapping
        assertEqual("my-custom-example", code3);
        assertTrue(!code1.equals(code3));

        db.clear();
    }

    private static void testConcurrency() throws Exception {
        Database db = new Database(TEST_DB_FILE);
        db.clear();
        UrlShortenerService service = new UrlShortenerService(db, "localhost", 8080);

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
                        // Create a unique URL per operation
                        String url = "https://example.com/thread-" + threadId + "-op-" + j;
                        Database.UrlMapping mapping = service.shortenUrl(url, null);
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
        // There should be exactly 500 unique short codes.
        assertEqual(500, generatedCodes.size());
        assertEqual(500, db.getAllMappings().size());

        // Verify reloading db contains all of them
        Database dbReload = new Database(TEST_DB_FILE);
        assertEqual(500, dbReload.getAllMappings().size());

        db.clear();
    }

    // --- ASSERTIONS ---

    private static void assertEqual(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "] but was [" + actual + "]");
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true but was false");
        }
    }

    private static void assertFalse(boolean condition) {
        if (condition) {
            throw new AssertionError("Expected false but was true");
        }
    }
}
