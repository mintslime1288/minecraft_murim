package com.murimrpg.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class GanghoCommandLayoutTest {
    @Test
    void exposesOnePrimaryHubWithOnlyFrequentShortcuts() {
        assertEquals("강호", GanghoCommandLayout.ROOT_ALIASES.getFirst());
        assertTrue(GanghoCommandLayout.CHARACTER_SHORTCUTS.containsAll(java.util.List.of("내정보", "경지")));
        assertTrue(GanghoCommandLayout.PROGRESSION_SHORTCUTS.containsAll(java.util.List.of("문파", "무공", "내공", "운기조식")));
        var names = new HashSet<String>();
        names.addAll(GanghoCommandLayout.ROOT_ALIASES);
        names.addAll(GanghoCommandLayout.CHARACTER_SHORTCUTS);
        names.addAll(GanghoCommandLayout.PROGRESSION_SHORTCUTS);
        assertEquals(9, names.size(), "root aliases and shortcuts must not overlap");
    }
}
