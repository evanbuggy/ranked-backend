    package com.shared.entities;
//authored by Liam Kelly, 22346317

    public class ImportedMatch {
        private final int winnerId;
        private final int loserId;

        public ImportedMatch(int winnerId, int loserId) {
            this.winnerId = winnerId;
            this.loserId = loserId;
        }

        public int winnerId() { return winnerId; }
        public int loserId()  { return loserId; }
    }