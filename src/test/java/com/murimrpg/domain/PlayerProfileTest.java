package com.murimrpg.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {
    private static final UUID UUID_VALUE = UUID.fromString("1f80b65e-0250-45ef-b83b-05bb7293ed63");
    private static final Instant CREATED_AT = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void createsNewProfileWithRequiredDefaults() {
        PlayerProfile profile = PlayerProfile.createDefault(UUID_VALUE, CREATED_AT);

        assertEquals(MajorRealm.THIRD_RATE, profile.majorRealm());
        assertEquals(RealmPhase.INITIATE, profile.phase());
        assertEquals(0, profile.cultivationExp());
        assertTrue(profile.completedRequirements().isEmpty());
        assertEquals(Set.of("QI_BAR"), profile.unlockedContent());
        assertEquals(CREATED_AT, profile.createdAt());
        assertEquals(CREATED_AT, profile.updatedAt());
    }

    @Test
    void rejectsInvalidRealmCombination() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerProfile(
                UUID_VALUE,
                MajorRealm.THIRD_RATE,
                RealmPhase.EARLY,
                0,
                Set.of(),
                Set.of("QI_BAR"),
                CREATED_AT,
                CREATED_AT
        ));
    }

    @Test
    void rejectsNegativeCultivationExp() {
        assertThrows(IllegalArgumentException.class, () -> PlayerProfile.createDefault(UUID_VALUE, CREATED_AT)
                .withProgress(MajorRealm.THIRD_RATE, RealmPhase.BEGINNER, -1, CREATED_AT));
    }

    @Test
    void resetsAllProgressToDefaults() {
        PlayerProfile profile = new PlayerProfile(
                UUID_VALUE,
                MajorRealm.SECOND_RATE,
                RealmPhase.MIDDLE,
                900,
                Set.of("TRIAL_ONE"),
                Set.of("QI_BAR", "SECOND_RATE_SKILL"),
                CREATED_AT,
                CREATED_AT.plusSeconds(1)
        );

        PlayerProfile reset = profile.resetProgress(CREATED_AT.plusSeconds(2));

        assertEquals(MajorRealm.THIRD_RATE, reset.majorRealm());
        assertEquals(RealmPhase.INITIATE, reset.phase());
        assertEquals(0, reset.cultivationExp());
        assertTrue(reset.completedRequirements().isEmpty());
        assertEquals(Set.of("QI_BAR"), reset.unlockedContent());
        assertEquals(CREATED_AT, reset.createdAt());
    }
}
