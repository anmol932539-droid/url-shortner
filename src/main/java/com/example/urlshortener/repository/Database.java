package com.example.urlshortener.repository;

import com.example.urlshortener.model.UrlMapping;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class Database {
    private final Map<String, UrlMapping> mappings = new ConcurrentHashMap<>();
    // Reverse map: originalUrl → UrlMapping (only for non-custom/auto-generated codes)
    private final Map<String, UrlMapping> reverseMap = new ConcurrentHashMap<>();

    public Optional<UrlMapping> get(String shortCode) {
        return Optional.ofNullable(mappings.get(shortCode));
    }

    public boolean exists(String shortCode) {
        return mappings.containsKey(shortCode);
    }

    public void put(UrlMapping mapping) {
        mappings.put(mapping.getShortCode(), mapping);
        if (!mapping.isCustom()) {
            reverseMap.put(mapping.getOriginalUrl(), mapping);
        }
    }

    /**
     * Atomically inserts the mapping only if the alias doesn't already exist.
     * @return the existing mapping if alias was taken, or null if insert succeeded.
     */
    public UrlMapping putIfAbsent(UrlMapping mapping) {
        UrlMapping existing = mappings.putIfAbsent(mapping.getShortCode(), mapping);
        if (existing == null && !mapping.isCustom()) {
            reverseMap.putIfAbsent(mapping.getOriginalUrl(), mapping);
        }
        return existing;
    }

    public Optional<UrlMapping> findByOriginalUrl(String originalUrl) {
        return Optional.ofNullable(reverseMap.get(originalUrl));
    }

    public void incrementClicks(String shortCode) {
        // Clicks implementation removed
    }

    public Map<String, UrlMapping> getAllMappings() {
        return Map.copyOf(mappings);
    }

    public void clear() {
        mappings.clear();
        reverseMap.clear();
    }
}
