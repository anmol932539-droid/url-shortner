package com.example.urlshortener.dto;

public class ShortenRequest {
    private String url;
    private String alias;

    public ShortenRequest() {
    }

    public ShortenRequest(String url, String alias) {
        this.url = url;
        this.alias = alias;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
