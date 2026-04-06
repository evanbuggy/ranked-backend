package com.shared.entities;

import com.shared.valueObjects.StartGgId;

public class ImportedMatch {
    private final StartGgId winnerId;
    private final StartGgId loserId;

    public ImportedMatch(StartGgId winnerId, StartGgId loserId) {
        this.winnerId = winnerId;
        this.loserId = loserId;
    }

    public StartGgId winnerId() { return winnerId; }
    public StartGgId loserId()  { return loserId; }
}