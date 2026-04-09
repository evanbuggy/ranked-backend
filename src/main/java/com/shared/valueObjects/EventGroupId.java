
package com.shared.valueObjects;
//authored by Liam Kelly, 22346317

public record EventGroupId(int value) {
    public EventGroupId {
        if (value <= 0) throw new IllegalArgumentException("EventGroupId must be positive");
    }
}