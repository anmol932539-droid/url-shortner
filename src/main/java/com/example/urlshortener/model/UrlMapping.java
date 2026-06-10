package com.example.urlshortener.model;

public record UrlMapping(
    String shortCode,
    String originalUrl,
    boolean isCustom,
    long createdAt
) {}
