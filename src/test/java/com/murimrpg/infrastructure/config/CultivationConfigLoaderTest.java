package com.murimrpg.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.murimrpg.domain.CultivationProgression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CultivationConfigLoaderTest {
    private static final List<String> EXPECTED_STAGE_IDS = List.of(
            "third_rate_initiate",
            "third_rate_beginner",
            "third_rate_intermediate",
            "third_rate_peak",
            "second_rate_early",
            "second_rate_middle",
            "second_rate_late",
            "second_rate_complete",
            "first_rate_early",
            "first_rate_middle",
            "first_rate_late",
            "first_rate_peak"
    );
    private static final List<String> EXPECTED_UNLOCKS = List.of(
            "QI_BAR",
            "CRIMSON_COMBO",
            "CLOUD_STEP",
            "SECOND_RATE_TRIAL_ACCESS",
            "QI_REGEN_1",
            "FALLING_PETAL_COUNTER",
            "AERIAL_STEP",
            "BLACK_PINE_CHIEF_ACCESS",
            "SWORD_AURA_VISUAL",
            "MAX_QI_1",
            "MOVEMENT_UPGRADE_1",
            "MVP_COMPLETE_TITLE"
    );

    @TempDir
    Path tempDirectory;

    private final CultivationConfigLoader loader = new CultivationConfigLoader();

    @Test
    void loadsTwelveStagesInExactConfiguredOrder() {
        CultivationProgression progression = loader.load(Path.of("src/main/resources/realms.yml"));

        assertEquals(EXPECTED_STAGE_IDS, progression.stages().stream().map(stage -> stage.stageId()).toList());
        assertEquals(
                List.of(0L, 100L, 250L, 450L, 700L, 1000L, 1350L, 1750L, 2200L, 2700L, 3300L, 4000L),
                progression.stages().stream().map(stage -> stage.requiredCultivationExp()).toList()
        );
        assertEquals(
                EXPECTED_UNLOCKS,
                progression.stages().stream().map(stage -> stage.unlocks().iterator().next()).toList()
        );
        assertEquals(
                "training_trial",
                progression.stage("second_rate_early").promotionRequirements().iterator().next().id()
        );
        assertEquals(
                "black_pine_chief",
                progression.stage("first_rate_early").promotionRequirements().iterator().next().id()
        );
    }

    @Test
    void rejectsDuplicateStageId() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("same_stage", "THIRD_RATE", "INITIATE", 1, "same_stage"),
                stage("same_stage", "THIRD_RATE", "BEGINNER", 2, null)
        ));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    @Test
    void rejectsDuplicateOrder() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second"),
                stage("second", "THIRD_RATE", "BEGINNER", 1, null)
        ));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    @Test
    void rejectsInvalidRealmCombination() throws Exception {
        Path file = write("realms:\n  - " + stage("invalid", "THIRD_RATE", "EARLY", 1, null));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    @Test
    void rejectsInvalidNextStageConnection() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "missing_stage"),
                stage("second", "THIRD_RATE", "BEGINNER", 2, null)
        ));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    private Path write(String yaml) throws Exception {
        Path file = tempDirectory.resolve("realms.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private String stage(String id, String major, String phase, int order, String next) {
        String nextLine = next == null ? "" : "\n    next-stage-id: " + next;
        return """
                stage-id: %s
                    major-realm-id: %s
                    phase-id: %s
                    display-name: test
                    order: %d
                    required-cultivation-exp: 0
                    max-qi: 100
                    qi-regeneration: 1
                    promotion-requirements: []
                    unlocks: [QI_BAR]%s
                """.formatted(id, major, phase, order, nextLine).stripTrailing();
    }
}
