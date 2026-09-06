package com.murimrpg.domain;

/** Visual-only values. Combat and progression values never live here. */
public record EffectDefinition(String id, String displayName, int durationTicks, float scale, int maxDistance, int particleCount) {
    public EffectDefinition {
        if (id == null || id.isBlank() || displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Effect id and name are required");
        if (durationTicks < 1 || scale <= 0 || maxDistance < 1 || particleCount < 1) throw new IllegalArgumentException("Invalid visual effect values: " + id);
    }
}
