package com.example.urlshortener;

import com.example.urlshortener.dto.ShortenRequest;
import com.example.urlshortener.dto.ShortenResponse;
import com.example.urlshortener.repository.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that boot the full Spring Boot server on a random port
 * and make real HTTP requests to the API endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UrlShortenerIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Database database;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        database.clear();
    }

    // ---- POST /shorten ----

    @Test
    void shortenUrl_withValidUrl_returns201() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", null);
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("https://www.google.com", response.getBody().getOriginalUrl());
        assertNotNull(response.getBody().getCode());
        assertFalse(response.getBody().isCustom());
    }

    @Test
    void shortenUrl_withCustomAlias_returns201() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", "my-google");
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("my-google", response.getBody().getCode());
        assertTrue(response.getBody().isCustom());
    }

    @Test
    void shortenUrl_withMissingUrl_returns400() {
        ShortenRequest request = new ShortenRequest(null, null);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    void shortenUrl_withEmptyUrl_returns400() {
        ShortenRequest request = new ShortenRequest("   ", null);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shortenUrl_withInvalidUrl_returns400() {
        ShortenRequest request = new ShortenRequest("ftp://not-http.com", null);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("Malformed"));
    }

    @Test
    void shortenUrl_duplicateUrlReturnsSameCode() {
        ShortenRequest request = new ShortenRequest("https://www.example.com", null);

        ResponseEntity<ShortenResponse> first = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );
        ResponseEntity<ShortenResponse> second = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals(first.getBody().getCode(), second.getBody().getCode());
    }

    @Test
    void shortenUrl_sameAliasSameUrlReturnsExistingMapping() {
        ShortenRequest request = new ShortenRequest("https://www.example.com", "my-link");

        ResponseEntity<ShortenResponse> first = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );
        ResponseEntity<ShortenResponse> second = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertEquals("my-link", second.getBody().getCode());
    }

    @Test
    void shortenUrl_sameAliasDifferentUrlReturns409() {
        // First request — alias "taken" for URL A
        ShortenRequest request1 = new ShortenRequest("https://www.google.com", "taken");
        restTemplate.postForEntity(baseUrl() + "/shorten", request1, ShortenResponse.class);

        // Second request — alias "taken" but for URL B
        ShortenRequest request2 = new ShortenRequest("https://www.github.com", "taken");
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request2, Map.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("already in use"));
    }

    @Test
    void shortenUrl_withInvalidAlias_returns400() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", "ab");  // too short
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shortenUrl_withReservedAlias_returns400() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", "shorten");
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // ---- GET /{code} (Redirect) ----

    @Test
    void redirect_withValidCode_returns301() {
        // First, shorten a URL
        ShortenRequest request = new ShortenRequest("https://www.google.com", "goog");
        restTemplate.postForEntity(baseUrl() + "/shorten", request, ShortenResponse.class);

        // Hit the redirect endpoint (don't follow redirects)
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.exchange(
            baseUrl() + "/goog", HttpMethod.GET, entity, String.class
        );

        assertEquals(HttpStatus.MOVED_PERMANENTLY, response.getStatusCode());
        assertEquals("https://www.google.com", response.getHeaders().getLocation().toString());
    }

    @Test
    void redirect_withUnknownCode_returns404Json() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/nonexistent", Map.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
        assertEquals("Short code not found", response.getBody().get("error"));
    }

    // ---- Edge Cases ----

    @Test
    void shortenUrl_selfReferencingUrlIsRejected() {
        // Try to shorten a URL pointing back to the service itself
        ShortenRequest request = new ShortenRequest(baseUrl() + "/some-code", null);
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void shortenUrl_responseContainsFullShortUrl() {
        ShortenRequest request = new ShortenRequest("https://www.example.com", "demo");
        ResponseEntity<ShortenResponse> response = restTemplate.postForEntity(
            baseUrl() + "/shorten", request, ShortenResponse.class
        );

        assertNotNull(response.getBody().getShortUrl());
        assertTrue(response.getBody().getShortUrl().contains("/demo"));
    }
}
