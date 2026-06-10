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
        mappings.put(mapping.shortCode(), mapping);
    }

    public Optional<UrlMapping> findByOriginalUrl(String originalUrl) {
        return mappings.values().stream()
            .filter(m -> !m.isCustom() && m.originalUrl().equals(originalUrl))
            .findFirst();
    }

    public void incrementClicks(String shortCode) {
        mappings.computeIfPresent(shortCode, (k, mapping) -> new UrlMapping(
            mapping.shortCode(),
            mapping.originalUrl(),
            mapping.isCustom(),
            mapping.createdAt(),
            mapping.clicks() + 1
        ));
    }

    public Map<String, UrlMapping> getAllMappings() {
        return Map.copyOf(mappings);
    }

    public void clear() {
        mappings.clear();
    }
}
