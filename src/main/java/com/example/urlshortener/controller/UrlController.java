package com.example.urlshortener.controller;

import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class UrlController {
    private final UrlShortenerService service;

    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    public record ShortenRequest(String url, String alias) {}

    public record ShortenResponse(
        String short_url,
        String code,
        String original_url,
        boolean is_custom,
        long created_at,
        int clicks
    ) {}

    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@RequestBody ShortenRequest request, HttpServletRequest servletRequest) {
        String originalUrl = request.url();
        String customAlias = request.alias();

        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required parameter: url"));
        }

        try {
            UrlMapping mapping = service.shortenUrl(originalUrl.trim(), customAlias != null ? customAlias.trim() : null);
            
            // Reconstruct the request's host to create the absolute short url
            String host = servletRequest.getHeader("Host");
            if (host == null) {
                host = "localhost:8080";
            }
            String scheme = servletRequest.getScheme(); // http or https
            String shortUrl = scheme + "://" + host + "/" + mapping.shortCode();

            ShortenResponse response = new ShortenResponse(
                shortUrl,
                mapping.shortCode(),
                mapping.originalUrl(),
                mapping.isCustom(),
                mapping.createdAt(),
                mapping.clicks()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }

    @GetMapping("/analytics/{code}")
    public ResponseEntity<?> getAnalytics(@PathVariable String code) {
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Short code missing from analytics path."));
        }

        UrlMapping mapping = service.getMapping(code.trim());
        if (mapping == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Short code not found."));
        }

        ShortenResponse response = new ShortenResponse(
            null,
            mapping.shortCode(),
            mapping.originalUrl(),
            mapping.isCustom(),
            mapping.createdAt(),
            mapping.clicks()
        );

        return ResponseEntity.ok(response);
    }
}
