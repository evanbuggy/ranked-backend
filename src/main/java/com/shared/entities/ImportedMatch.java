    package com.shared.entities;
//authored by Liam Kelly, 22346317

import com.fasterxml.jackson.annotation.JsonCreator;

public class ImportedMatch {
        private final int winnerId;
        private final int loserId;
        @JsonCreator
        public ImportedMatch(int winnerId, int loserId) {
            this.winnerId = winnerId;
            this.loserId = loserId;
        }

        public int getWinnerId() { return winnerId; }
        public int getLoserId()  { return loserId; }
    }