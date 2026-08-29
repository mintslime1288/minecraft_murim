package com.murimrpg.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.murimrpg.domain.CultivationProgression;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CultivationConfigLoaderTest {
    private static final List<String> EXISTING_STAGE_IDS = List.of(
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
    private static final List<String> ADVANCED_STAGE_IDS = List.of(
            "jeoljeong_realm_early",
            "jeoljeong_realm_middle",
            "jeoljeong_realm_late",
            "jeoljeong_realm_complete",
            "chojeoljeong_realm_early",
            "chojeoljeong_realm_middle",
            "chojeoljeong_realm_late",
            "chojeoljeong_realm_complete",
            "hwagyeong_realm_early",
            "hwagyeong_realm_middle",
            "hwagyeong_realm_late",
            "hwagyeong_realm_complete",
            "hyeongyeong_realm_early",
            "hyeongyeong_realm_middle",
            "hyeongyeong_realm_late",
            "hyeongyeong_realm_complete",
            "saengsagyeong_realm_early",
            "saengsagyeong_realm_middle",
            "saengsagyeong_realm_late",
            "saengsagyeong_realm_complete"
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
    void loadsThirtyTwoStagesInExactConfiguredOrder() {
        CultivationProgression progression = loader.load(Path.of("src/main/resources/realms.yml"));

        List<String> expectedStageIds = java.util.stream.Stream
                .concat(EXISTING_STAGE_IDS.stream(), ADVANCED_STAGE_IDS.stream())
                .toList();
        assertEquals(32, progression.stages().size());
        assertEquals(expectedStageIds, progression.stages().stream().map(stage -> stage.stageId()).toList());
        assertEquals(EXISTING_STAGE_IDS, progression.stages().subList(0, 12).stream()
                .map(stage -> stage.stageId()).toList());
        assertEquals(ADVANCED_STAGE_IDS, progression.stages().subList(12, 32).stream()
                .map(stage -> stage.stageId()).toList());
        assertEquals(
                List.of(
                        0L, 100L, 250L, 450L, 700L, 1000L, 1350L, 1750L,
                        2200L, 2700L, 3300L, 4000L, 5000L, 6500L, 8000L, 10000L,
                        12500L, 15500L, 19000L, 23000L, 28000L, 34000L, 41000L, 50000L,
                        62000L, 76000L, 93000L, 115000L, 145000L, 180000L, 225000L, 280000L
                ),
                progression.stages().stream().map(stage -> stage.requiredCultivationExp()).toList()
        );
        assertEquals(
                EXPECTED_UNLOCKS,
                progression.stages().subList(0, 12).stream()
                        .map(stage -> stage.unlocks().iterator().next()).toList()
        );
        assertTrue(progression.stages().subList(12, 32).stream().allMatch(stage -> stage.unlocks().isEmpty()));
        assertEquals(
                List.of("training_trial", "black_pine_chief", "jeoljeong_breakthrough_trial",
                        "chojeoljeong_breakthrough_trial", "hwagyeong_breakthrough_trial",
                        "hyeongyeong_breakthrough_trial", "saengsagyeong_breakthrough_trial"),
                progression.stages().stream()
                        .flatMap(stage -> stage.promotionRequirements().stream())
                        .map(requirement -> requirement.id()).toList()
        );
        assertEquals("saengsagyeong_realm_complete", progression.finalStage().stageId());
        assertEquals("생사경·원만", progression.finalStage().displayName());
        assertEquals(280000, progression.finalStage().requiredCultivationExp());
        assertTrue(progression.finalStage().nextStageId().isEmpty());
    }

    @Test
    void ordersConnectionsAndQiValuesAreValidAcrossAllStages() {
        CultivationProgression progression = loader.load(Path.of("src/main/resources/realms.yml"));

        for (int index = 0; index < progression.stages().size(); index++) {
            var stage = progression.stages().get(index);
            assertEquals(index + 1, stage.order());
            assertTrue(stage.maxQi() > 0);
            assertTrue(stage.qiRegeneration() > 0);
            if (index > 0) {
                var previous = progression.stages().get(index - 1);
                assertTrue(stage.maxQi() >= previous.maxQi());
                assertTrue(stage.qiRegeneration() >= previous.qiRegeneration());
            }
            if (index + 1 < progression.stages().size()) {
                assertEquals(progression.stages().get(index + 1).stageId(), stage.nextStageId().orElseThrow());
            }
        }
    }

    @Test
    void rejectsUnexpectedDefaultStageCountWithoutRestrictingGenericLoader() throws Exception {
        Path file = write("realms:\n  - " + stage(
                "only_stage", "THIRD_RATE", "INITIATE", 1, null
        ));

        assertEquals(1, loader.load(file).stages().size());
        assertThrows(IllegalArgumentException.class, () -> loader.load(file, 32));
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
    void rejectsDuplicateRealmCombination() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second"),
                stage("second", "THIRD_RATE", "INITIATE", 2, null)
        ));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    @Test
    void rejectsNonContiguousOrder() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second"),
                stage("second", "THIRD_RATE", "BEGINNER", 3, null)
        ));

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

    @Test
    void rejectsDecreasingCumulativeExp() throws Exception {
        Path file = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second", 100, 100, 1),
                stage("second", "THIRD_RATE", "BEGINNER", 2, null, 99, 100, 1)
        ));

        assertThrows(IllegalArgumentException.class, () -> loader.load(file));
    }

    @Test
    void rejectsNegativeExpAndInvalidRequirementId() throws Exception {
        Path negativeExp = write("realms:\n  - "
                + stage("invalid", "THIRD_RATE", "INITIATE", 1, null, -1, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> loader.load(negativeExp));

        Path invalidRequirement = write("realms:\n  - "
                + stage("invalid", "THIRD_RATE", "INITIATE", 1, null)
                        .replace("promotion-requirements: []", "promotion-requirements: [Invalid-ID]"));
        assertThrows(IllegalArgumentException.class, () -> loader.load(invalidRequirement));
    }

    @Test
    void rejectsZeroQiValues() throws Exception {
        Path zeroMaxQi = write("realms:\n  - "
                + stage("invalid", "THIRD_RATE", "INITIATE", 1, null, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> loader.load(zeroMaxQi));

        Path zeroRegeneration = write("realms:\n  - "
                + stage("invalid", "THIRD_RATE", "INITIATE", 1, null, 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> loader.load(zeroRegeneration));
    }

    @Test
    void rejectsDecreasingQiValues() throws Exception {
        Path decreasingMaxQi = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second", 0, 100, 2),
                stage("second", "THIRD_RATE", "BEGINNER", 2, null, 1, 99, 2)
        ));
        assertThrows(IllegalArgumentException.class, () -> loader.load(decreasingMaxQi));

        Path decreasingRegeneration = write("""
                realms:
                  - %s
                  - %s
                """.formatted(
                stage("first", "THIRD_RATE", "INITIATE", 1, "second", 0, 100, 2),
                stage("second", "THIRD_RATE", "BEGINNER", 2, null, 1, 100, 1)
        ));
        assertThrows(IllegalArgumentException.class, () -> loader.load(decreasingRegeneration));
    }

    private Path write(String yaml) throws Exception {
        Path file = tempDirectory.resolve("realms.yml");
        Files.writeString(file, yaml);
        return file;
    }

    private String stage(String id, String major, String phase, int order, String next) {
        return stage(id, major, phase, order, next, 0, 100, 1);
    }

    private String stage(
            String id,
            String major,
            String phase,
            int order,
            String next,
            long exp,
            long maxQi,
            long qiRegeneration
    ) {
        String nextLine = next == null ? "" : "\n    next-stage-id: " + next;
        return """
                stage-id: %s
                    major-realm-id: %s
                    phase-id: %s
                    display-name: test
                    order: %d
                    required-cultivation-exp: %d
                    max-qi: %d
                    qi-regeneration: %d
                    promotion-requirements: []
                    unlocks: [QI_BAR]%s
                """.formatted(id, major, phase, order, exp, maxQi, qiRegeneration, nextLine).stripTrailing();
    }
}
