package com.startGgIntegration.systemEvents;
//authored by Liam Kelly, 22346317

import com.shared.entities.ImportedMatch;
import java.util.List;
import com.shared.entities.StartGgEvent;
import com.shared.entities.Player;

public record EventImported(
    int importId,
    int eventGroupId,
    List<ImportedMatch> matches,
    List<Player> players,
    StartGgEvent myEvent
) {}