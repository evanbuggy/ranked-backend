package com.startGgIntegration;
//authored by Liam Kelly, 22346317

import java.util.Optional;

public interface EventImportRepo {
    EventImport save(EventImport eventImport);
    Optional<EventImport> findById(int id);
    void delete(int id);
}
