package com.startGgIntegration;

import com.startGgIntegration.systemEvents.EventImported;
import com.startGgIntegration.systemEvents.ImportFailure;
import com.shared.entities.ImportedMatch;
import com.shared.valueObjects.StartGgId;
import com.startGgIntegration.valueObjects.StartGgURL;
import com.startGgIntegration.valueObjects.ImportStatus;

import java.util.ArrayList;
import java.util.List;

public class EventImport {

    private int eventImportId;
    private StartGgUrl startGgUrl;
    private ImportStatus status;
    private EventGroupId eventGroupId;
    private List<ImportedMatch> matches = new ArrayList<>();

    // Domain events to be dispatched after persistence
    private final List<Object> domainEvents = new ArrayList<>();

    // Factory method — matches createEvent() on the diagram
    public static EventImport createEvent(String url, int eventGroupId) {
        EventImport ei = new EventImport();
        ei.startGgUrl  = new StartGgUrl(url);
        ei.eventGroupId = new EventGroupId(eventGroupId);
        ei.status = ImportStatus.PENDING;
        return ei;
    }

    public void status_inprogress() {
        this.status = ImportStatus.IN_PROGRESS;
    }

    public void status_complete(List<ImportedMatch> matches) {
        this.matches = new ArrayList<>(matches);
        this.status = ImportStatus.COMPLETE;
        domainEvents.add(new EventImported(eventImportId, eventGroupId.value(), this.matches));
    }

    public void status_fail(String reason) {
        this.status = ImportStatus.FAILED;
        domainEvents.add(new ImportFailure(eventImportId, reason));
    }

    // Getters
    public int getEventImportId()        { return eventImportId; }
    public void setEventImportId(int id) { this.eventImportId = id; }
    public StartGgUrl getStartGgUrl()    { return startGgUrl; }
    public ImportStatus getStatus()      { return status; }
    public EventGroupId getEventGroupId(){ return eventGroupId; }
    public List<ImportedMatch> getMatches() { return matches; }
    public List<Object> pullDomainEvents() {
        List<Object> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }
}
