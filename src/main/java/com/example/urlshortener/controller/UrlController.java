package com.example.urlshortener.controller;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
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

    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@RequestBody ShortenRequest request, HttpServletRequest servletRequest) {
        if (!isValidRequest(request)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required parameter: url"));
        }
        try {
            return processShortenRequest(request, servletRequest);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }

    private boolean isValidRequest(ShortenRequest request) {
        String originalUrl = request.getUrl();
        return originalUrl != null && !originalUrl.trim().isEmpty();
    }

    private ResponseEntity<?> processShortenRequest(ShortenRequest request, HttpServletRequest servletRequest) throws Exception {
        String originalUrl = request.getUrl().trim();
        String alias = request.getAlias();
        String customAlias = alias != null ? alias.trim() : null;
        
        UrlMapping mapping = service.shortenUrl(originalUrl, customAlias);
        ShortenResponse response = createResponse(mapping, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private ShortenResponse createResponse(UrlMapping mapping, HttpServletRequest request) {
        String shortUrl = buildAbsoluteUrl(request, mapping.getShortCode());
        return new ShortenResponse(
            shortUrl,
            mapping.getShortCode(),
            mapping.getOriginalUrl(),
            mapping.isCustom(),
            mapping.getCreatedAt()
        );
    }

    private String buildAbsoluteUrl(HttpServletRequest request, String shortCode) {
        String host = request.getHeader("Host");
        if (host == null) {
            host = "localhost:8080";
        }
        return request.getScheme() + "://" + host + "/" + shortCode;
    }
}
