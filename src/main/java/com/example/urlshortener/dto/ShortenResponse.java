package com.example.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShortenResponse {
    @JsonProperty("short_url")
    private String shortUrl;
    
    private String code;
    
    @JsonProperty("original_url")
    private String originalUrl;
    
    @JsonProperty("custom")
    private boolean isCustom;
    
    @JsonProperty("created_at")
    private long createdAt;

    public ShortenResponse() {
    }

    public ShortenResponse(String shortUrl, String code, String originalUrl, boolean isCustom, long createdAt) {
        this.shortUrl = shortUrl;
        this.code = code;
        this.originalUrl = originalUrl;
        this.isCustom = isCustom;
        this.createdAt = createdAt;
    }

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
