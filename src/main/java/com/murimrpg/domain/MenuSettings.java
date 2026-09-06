package com.murimrpg.domain;

/** Server-owned menu presentation settings. Warp locations are deliberately not stored here. */
public record MenuSettings(long cooldownMillis, String title, String spawnMessage, String trainingMessage) {
    public MenuSettings {
        if (cooldownMillis < 0) throw new IllegalArgumentException("menu cooldown cannot be negative");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("menu title is required");
    }
}
