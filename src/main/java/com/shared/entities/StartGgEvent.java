package com.shared.entities;
//authored by Liam Kelly, 22346317

import com.fasterxml.jackson.annotation.JsonCreator;

public class StartGgEvent {
    String tournamentName;
    int tournamentId;
    String eventName;
    int eventId;
    long eventDate;
    @JsonCreator
    public StartGgEvent(String tournamentName, int tournamentId, String eventName, int eventId, long eventDate) {
        this.tournamentName = tournamentName;
        this.tournamentId = tournamentId;
        this.eventName = eventName;
        this.eventId = eventId;
        this.eventDate = eventDate;
    }

    public String getTournamentName() { return tournamentName; }
    public int getTournamentId() { return tournamentId; }
    public String getEventName() { return eventName; }
    public int getEventId() { return eventId; }
    public long getEventDate() { return eventDate; }
}