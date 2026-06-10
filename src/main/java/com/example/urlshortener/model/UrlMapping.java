package com.example.urlshortener.model;

public class UrlMapping {
    private String shortCode;
    private String originalUrl;
    private boolean isCustom;
    private long createdAt;

    public UrlMapping() {
    }

    public UrlMapping(String shortCode, String originalUrl, boolean isCustom, long createdAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.isCustom = isCustom;
        this.createdAt = createdAt;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
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
