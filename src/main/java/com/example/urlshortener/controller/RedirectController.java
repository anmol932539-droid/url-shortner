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

        UrlMapping mapping = service.resolveAndTrack(code);
        if (mapping != null) {
            return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY) // 301 Redirect
                    .header(HttpHeaders.LOCATION, mapping.originalUrl())
                    .build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(get404Html(code));
        }
    }

    private String get404Html(String code) {
        return String.format("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>404 - Link Not Found</title>
                <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;600;700&family=Space+Grotesk:wght@700&display=swap" rel="stylesheet">
                <style>
                    body {
                        font-family: 'Outfit', sans-serif;
                        background-color: #080710;
                        color: #ffffff;
                        height: 100vh;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        justify-content: center;
                        margin: 0;
                        text-align: center;
                        position: relative;
                        overflow: hidden;
                    }
                    .blob {
                        position: absolute;
                        width: 400px;
                        height: 400px;
                        border-radius: 50%;
                        filter: blur(150px);
                        z-index: -1;
                        opacity: 0.15;
                        background: #6366f1;
                    }
                    h1 {
                        font-family: 'Space Grotesk', sans-serif;
                        font-size: 6rem;
                        margin: 0;
                        background: linear-gradient(135deg, #ef4444 0%%, #a855f7 100%%);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                    }
                    h2 {
                        font-size: 1.8rem;
                        margin: 1rem 0;
                        font-weight: 500;
                    }
                    p {
                        color: #a0aec0;
                        max-width: 500px;
                        margin-bottom: 2rem;
                        line-height: 1.6;
                    }
                    .btn-home {
                        background: linear-gradient(135deg, #6366f1 0%%, #a855f7 100%%);
                        color: white;
                        text-decoration: none;
                        padding: 0.75rem 2rem;
                        border-radius: 50px;
                        font-weight: 600;
                        transition: transform 0.2s, box-shadow 0.2s;
                    }
                    .btn-home:hover {
                        transform: translateY(-2px);
                        box-shadow: 0 10px 20px rgba(99, 102, 241, 0.3);
                    }
                </style>
            </head>
            <body>
                <div class="blob"></div>
                <h1>404</h1>
                <h2>Link Not Found</h2>
                <p>The short link code <strong>%s</strong> could not be found in our database. It might have expired, or it was never created in the first place.</p>
                <a href="/" class="btn-home">Create New Link</a>
            </body>
            </html>
            """, code);
    }
}
