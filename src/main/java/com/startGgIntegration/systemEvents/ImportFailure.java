package com.startGgIntegration.systemEvents;
//authored by Liam Kelly, 22346317

public record ImportFailure(
    int importId,
    String reason
) {}
