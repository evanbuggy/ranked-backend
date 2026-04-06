package com.startGgIntegration.valueObjects;

public record StartGgURL(String value) {
    public StartGgURL {
        if (value == null || !value.contains("start.gg") || !value.contains("event"))
            throw new IllegalArgumentException("Invalid Start.gg URL: " + value);
    }
}