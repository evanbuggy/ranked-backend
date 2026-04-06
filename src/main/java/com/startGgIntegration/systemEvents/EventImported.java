package com.startGgIntegration.systemEvents;

import com.shared.entities.ImportedMatch;
import java.util.List;

public record EventImported(
    int importId,
    int eventGroupId,
    List<ImportedMatch> matches
) {}