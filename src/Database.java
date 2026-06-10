package src;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Database {
    public record UrlMapping(
        String shortCode,
        String originalUrl,
        boolean isCustom,
        long createdAt,
        int clicks
    ) {}

    private final Path dbPath;
    private final Map<String, UrlMapping> mappings = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Database(String dbFilePath) {
        this.dbPath = Path.of(dbFilePath);
        loadDatabase();
    }

    private void loadDatabase() {
        lock.writeLock().lock();
        try {
            if (!Files.exists(dbPath)) {
                return;
            }
            try (BufferedReader reader = Files.newBufferedReader(dbPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split(",", -1);
                    if (parts.length >= 5) {
                        String shortCode = parts[0];
                        String originalUrl = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                        boolean isCustom = Boolean.parseBoolean(parts[2]);
                        long createdAt = Long.parseLong(parts[3]);
                        int clicks = Integer.parseInt(parts[4]);
                        
                        mappings.put(shortCode, new UrlMapping(shortCode, originalUrl, isCustom, createdAt, clicks));
                    }
                }
            } catch (IOException | IllegalArgumentException e) {
                System.err.println("Warning: failed to load database file, starting fresh. Error: " + e.getMessage());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void save() {
        lock.readLock().lock();
        Path tempPath = dbPath.resolveSibling(
            dbPath.getFileName().toString() + "." + Thread.currentThread().threadId() + "." + System.nanoTime() + ".tmp"
        );
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                for (UrlMapping mapping : mappings.values()) {
                    String encodedUrl = URLEncoder.encode(mapping.originalUrl(), StandardCharsets.UTF_8);
                    writer.write(String.format("%s,%s,%b,%d,%d\n",
                        mapping.shortCode(),
                        encodedUrl,
                        mapping.isCustom(),
                        mapping.createdAt(),
                        mapping.clicks()
                    ));
                }
            }
            
            // Retry loop for Windows file system transient lock delays
            int retries = 0;
            while (true) {
                try {
                    Files.move(tempPath, dbPath, StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (IOException e) {
                    retries++;
                    if (retries > 5) {
                        throw e;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error saving database: " + e.getMessage());
        } finally {
            lock.readLock().unlock();
            // Cleanup temp file if it still exists due to an error before the move
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
        }
    }

    public Optional<UrlMapping> get(String shortCode) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(mappings.get(shortCode));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean exists(String shortCode) {
        lock.readLock().lock();
        try {
            return mappings.containsKey(shortCode);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(UrlMapping mapping) {
        lock.writeLock().lock();
        try {
            mappings.put(mapping.shortCode(), mapping);
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<UrlMapping> findByOriginalUrl(String originalUrl) {
        lock.readLock().lock();
        try {
            return mappings.values().stream()
                .filter(m -> !m.isCustom() && m.originalUrl().equals(originalUrl))
                .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void incrementClicks(String shortCode) {
        lock.writeLock().lock();
        try {
            UrlMapping mapping = mappings.get(shortCode);
            if (mapping != null) {
                mappings.put(shortCode, new UrlMapping(
                    mapping.shortCode(),
                    mapping.originalUrl(),
                    mapping.isCustom(),
                    mapping.createdAt(),
                    mapping.clicks() + 1
                ));
                save();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String, UrlMapping> getAllMappings() {
        lock.readLock().lock();
        try {
            return new HashMap<>(mappings);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            mappings.clear();
            save();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
