
package com.shared.valueObjects;

public record EventGroupId(int value) {
    public EventGroupId {
        if (value <= 0) throw new IllegalArgumentException("EventGroupId must be positive");
    }
}