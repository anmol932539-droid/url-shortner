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
        if ("favicon.ico".equalsIgnoreCase(code) || "index.html".equalsIgnoreCase(code)) {
            return ResponseEntity.notFound().build();
        }

        UrlMapping mapping = service.resolve(code);
        if (mapping != null) {
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY) // 301 Redirect
                    .header(HttpHeaders.LOCATION, mapping.originalUrl())
                    .build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\": \"Short code not found\"}");
        }
    }

}
