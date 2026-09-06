package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.murimrpg.domain.MenuSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class InteractiveMenuServiceTest {
    @Test void appliesConfiguredCooldownPerPlayer() {
        var service = new InteractiveMenuService(new MenuSettings(2500, "메뉴", "스폰", "수련"));
        UUID player = UUID.randomUUID();
        assertEquals(InteractiveMenuService.OpenResult.OPENED, service.tryOpen(player, 100));
        assertEquals(InteractiveMenuService.OpenResult.COOLDOWN, service.tryOpen(player, 2599));
        assertEquals(InteractiveMenuService.OpenResult.OPENED, service.tryOpen(player, 2600));
    }
}
