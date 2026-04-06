package com.startGgIntegration;

import java.util.Optional;

public interface EventImportRepo {
    EventImport save(EventImport eventImport);
    Optional<EventImport> findById(int id);
    void delete(int id);
}