package com.startGgIntegration;
//authored by Liam Kelly, 22346317

import com.startGgIntegration.systemEvents.EventImported;
import com.startGgIntegration.systemEvents.ImportFailure;
import com.shared.entities.*;
import com.shared.valueObjects.*;
import com.startGgIntegration.valueObjects.*;

import java.util.ArrayList;
import java.util.List;

public class EventImport {

    private int eventImportId;
    private StartGgURL startGgURL;
    private ImportStatus status;
    private EventGroupId eventGroupId;
    private List<ImportedMatch> matches = new ArrayList<>();
    private String failureCause;

    // Domain events to be dispatched after persistence
    private final List<Object> domainEvents = new ArrayList<>();

    // Factory method — matches createEvent() on the diagram
    public static EventImport createEvent(String url, int eventGroupId) {
        EventImport eventImport = new EventImport();
        try{
            eventImport.startGgURL  = new StartGgURL(url);
        }catch(IllegalArgumentException e){
            System.out.println("=== Failed to create EventImport: " + e.getMessage());
            eventImport.status_fail("Invalid Start.gg link! You must link to an event on Start.gg!");
            return eventImport;
        }
        
        eventImport.eventGroupId = new EventGroupId(eventGroupId);
        eventImport.status = ImportStatus.PENDING;
        return eventImport;
    }

    public void status_inprogress() {
        this.status = ImportStatus.IN_PROGRESS;
    }

    public void status_complete(List<ImportedMatch> matches, List<Player> players, StartGgEvent event) {
        this.matches = new ArrayList<>(matches);
        this.status = ImportStatus.COMPLETE;
        domainEvents.add(new EventImported(eventImportId, eventGroupId.value(), this.matches, players, event));
    }

    public void status_fail(String reason) {
        this.status = ImportStatus.FAILED;
        domainEvents.add(new ImportFailure(eventImportId, reason));
        this.failureCause = reason;
    }

    // Getters
    public int getEventImportId()        { return eventImportId; }
    public void setEventImportId(int id) { this.eventImportId = id; }
    public StartGgURL getStartGgURL()    { return startGgURL; }
    public ImportStatus getStatus()      { return status; }
    public EventGroupId getEventGroupId(){ return eventGroupId; }
    public List<ImportedMatch> getMatches() { return matches; }
    public List<Object> pullDomainEvents() {
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }

    public String getFailureCause()     { return this.failureCause; }
}
