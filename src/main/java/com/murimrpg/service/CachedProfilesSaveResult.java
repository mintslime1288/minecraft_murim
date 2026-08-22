package com.murimrpg.service;

import java.util.Map;
import java.util.UUID;

public record CachedProfilesSaveResult(int successCount, Map<UUID, Throwable> failures) {
    public CachedProfilesSaveResult {
        if (successCount < 0) {
            throw new IllegalArgumentException("successCount cannot be negative");
        }
        failures = Map.copyOf(failures);
    }

    public int failureCount() {
        return failures.size();
    }
}
