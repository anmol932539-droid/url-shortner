package com.example.urlshortener.repository;

import com.example.urlshortener.model.UrlMapping;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class Database {
    private final Map<String, UrlMapping> mappings = new ConcurrentHashMap<>();

    public Optional<UrlMapping> get(String shortCode) {
        return Optional.ofNullable(mappings.get(shortCode));
    }

    public boolean exists(String shortCode) {
        return mappings.containsKey(shortCode);
    }

    public void put(UrlMapping mapping) {
        mappings.put(mapping.getShortCode(), mapping);
    }

    public Optional<UrlMapping> findByOriginalUrl(String originalUrl) {
        return mappings.values().stream()
            .filter(m -> !m.isCustom() && m.getOriginalUrl().equals(originalUrl))
            .findFirst();
    }

    public void incrementClicks(String shortCode) {
        // Clicks implementation removed
    }

    public Map<String, UrlMapping> getAllMappings() {
        return Map.copyOf(mappings);
    }

    public void clear() {
        mappings.clear();
    }
}
