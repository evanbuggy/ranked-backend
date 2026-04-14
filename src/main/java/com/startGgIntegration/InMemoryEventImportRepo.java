package com.startGgIntegration;
//authored by Liam Kelly, 22346317

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryEventImportRepo implements EventImportRepo {
    private final Map<Integer, EventImport> store = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    @Override
    public EventImport save(EventImport e) {
        if (e.getEventImportId() == 0) e.setEventImportId(nextId.getAndIncrement());
        store.put(e.getEventImportId(), e);
        return e;
    }

    @Override
    public Optional<EventImport> findById(int id) { 
        return Optional.ofNullable(store.get(id)); 
    }

    @Override
    public void delete(int id) { 
        store.remove(id); 
    }
}