package com.example.urlshortener.controller;

import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RedirectController {
    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/{code}")
    public ResponseEntity<String> redirect(@PathVariable String code) {
        if (isIgnoredCode(code)) return ResponseEntity.notFound().build();
        UrlMapping mapping = service.resolve(code);
        return mapping != null ? buildRedirect(mapping) : buildNotFound();
    }

    private boolean isIgnoredCode(String code) {
        return "favicon.ico".equalsIgnoreCase(code) || "index.html".equalsIgnoreCase(code);
    }

    private ResponseEntity<String> buildRedirect(UrlMapping mapping) {
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, mapping.getOriginalUrl())
                .build();
    }

    private ResponseEntity<String> buildNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\": \"Short code not found\"}");
    }

}
