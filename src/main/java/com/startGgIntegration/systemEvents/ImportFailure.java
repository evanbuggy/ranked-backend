package com.startGgIntegration.systemEvents;

public record ImportFailure(
    int importId,
    String reason
) {}
