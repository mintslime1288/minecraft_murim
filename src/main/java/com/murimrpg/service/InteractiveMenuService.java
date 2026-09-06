package com.murimrpg.service;

import com.murimrpg.domain.MenuSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cooldown rule for the chat menu; command adapters only render the result. */
public final class InteractiveMenuService {
    public enum OpenResult { OPENED, COOLDOWN }
    private final MenuSettings settings;
    private final Map<UUID, Long> lastOpened = new ConcurrentHashMap<>();
    public InteractiveMenuService(MenuSettings settings) { this.settings = settings; }
    public OpenResult tryOpen(UUID playerId, long nowMillis) {
        Long previous = lastOpened.putIfAbsent(playerId, nowMillis);
        if (previous == null || nowMillis - previous >= settings.cooldownMillis()) { lastOpened.put(playerId, nowMillis); return OpenResult.OPENED; }
        return OpenResult.COOLDOWN;
    }
    public MenuSettings settings() { return settings; }
}
