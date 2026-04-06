package com.shared.valueObjects;

public record StartGgId(String value) {
    public StartGgId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("StartGgId cannot be blank");
    }
}