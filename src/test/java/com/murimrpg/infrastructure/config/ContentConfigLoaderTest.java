package com.murimrpg.infrastructure.config;

import static org.junit.jupiter.api.Assertions.*;

import com.murimrpg.domain.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentConfigLoaderTest {
    @TempDir Path tempDirectory;
    private final ContentConfigLoader loader = new ContentConfigLoader();
    private final CultivationProgression progression = new CultivationConfigLoader()
            .load(Path.of("src/main/resources/realms.yml"));

    @Test
    void loadsTwoAlliancesAndSixteenUniqueOrganizationsByCategory() {
        FactionCatalog factions = loadResources().factions();

        assertEquals(2, factions.alliances().size());
        assertEquals(Set.of("MURIM_ALLIANCE", "SADO_ALLIANCE"),
                factions.alliances().stream().map(AllianceDefinition::id).collect(Collectors.toSet()));
        assertEquals(16, factions.organizations().size());
        assertEquals(16, factions.organizations().stream().map(OrganizationDefinition::id).distinct().count());
        assertEquals(Set.of(
                "NAMGUNG_FAMILY", "HEBEI_PENG_FAMILY", "ZHUGE_FAMILY", "MURONG_FAMILY",
                "SICHUAN_TANG_FAMILY", "HUASHAN_SECT", "ZHONGNAN_SECT", "WUDANG_SECT",
                "SHAOLIN_TEMPLE", "BEGGARS_GANG", "GREEN_FOREST", "YANGTZE_RIVER_STRONGHOLDS",
                "ASSASSIN_HALL", "HAO_GATE", "DEMON_CULT", "BLOOD_CULT"
        ), factions.organizations().stream().map(OrganizationDefinition::id).collect(Collectors.toSet()));
        assertEquals(5, count(factions, OrganizationCategory.FIVE_GREAT_FAMILIES));
        assertEquals(5, count(factions, OrganizationCategory.NINE_SECTS_ONE_GANG));
        assertEquals(4, count(factions, OrganizationCategory.BLACK_PATH));
        assertEquals(2, count(factions, OrganizationCategory.HERETICAL_CULT));
        assertTrue(factions.organizations().stream()
                .filter(value -> value.type() == OrganizationType.SECT)
                .allMatch(value -> value.leaderTitle().filter(Set.of("문주", "장문인")::contains).isPresent()));
        assertTrue(factions.organizations().stream().allMatch(organization ->
                factions.findAlliance(organization.allianceId()).isPresent()
                        && factions.findCategory(organization.category()).isPresent()));
        OrganizationDefinition huashan = factions.findOrganization("HUASHAN_SECT").orElseThrow();
        assertEquals("華山派", huashan.hanjaName().orElseThrow());
        assertEquals(List.of("검법", "내공심법", "경공"), huashan.specialties());
    }

    @Test
    void loadsOnlyConfirmedRankSystemsInExactOrder() {
        FactionCatalog factions = loadResources().factions();

        assertRankIds(factions, "SECT_RANKS", List.of(
                "SECT_MASTER", "ELDER", "FIRST_GENERATION_DISCIPLE",
                "SECOND_GENERATION_DISCIPLE", "THIRD_GENERATION_DISCIPLE"
        ));
        assertRankIds(factions, "FAMILY_RANKS", List.of(
                "FAMILY_HEAD", "YOUNG_FAMILY_HEAD", "ELDER", "RISING_JUNIOR"
        ));
        assertRankIds(factions, "DEMON_CULT_RANKS", List.of(
                "HEAVENLY_DEMON", "YOUNG_CULT_LEADER", "COUNCIL_OF_ELDERS"
        ));
        assertEquals(3, factions.findRankSystem("DEMON_CULT_RANKS").orElseThrow().ranks().size());
        assertTrue(factions.findOrganization("BLOOD_CULT").orElseThrow().rankSystemId().isEmpty());
        assertTrue(factions.findOrganization("GREEN_FOREST").orElseThrow().rankSystemId().isEmpty());
    }

    @Test
    void loadsHuashanInternalArtsAndQuestGoals() {
        ContentCatalog content = loadResources();

        assertEquals(Set.of("LIUHE_ART", "PURPLE_MIST_DIVINE_ART"), content.skills().stream()
                .map(SkillDefinition::id).collect(Collectors.toSet()));
        assertTrue(content.skills().stream().allMatch(skill -> skill.type() == SkillType.INTERNAL_ART));
        assertEquals("기초", content.findSkill("LIUHE_ART").orElseThrow().grade());
        assertEquals(List.of("화산파 소속", "일대제자 승급 시험 합격"),
                content.findSkill("PURPLE_MIST_DIVINE_ART").orElseThrow().unlockConditions());
        assertEquals(4, content.quests().all().size());
        QuestDefinition entrance = content.quests().require("HUASHAN_ENTRANCE_TRIAL");
        assertEquals(10, entrance.requiredHits());
        assertEquals(10, entrance.requiredMeditationSeconds());
        assertEquals(MeditationGoalMode.CONTINUOUS, entrance.meditationMode());
        assertEquals("THIRD_GENERATION_DISCIPLE", entrance.resultingRankId().orElseThrow());
        assertEquals("LIUHE_ART", content.quests().require("LIUHE_ART_QUEST").grantedSkillId().orElseThrow());
        assertEquals("PURPLE_MIST_DIVINE_ART", content.quests().require("FIRST_DISCIPLE_PROMOTION_TRIAL")
                .grantedSkillId().orElseThrow());
        QuestDefinition second = content.quests().require("SECOND_DISCIPLE_PROMOTION_TRIAL");
        assertEquals(QuestObjectiveType.NPC_HEALTH_THRESHOLD, second.objectiveType());
        assertEquals(50.0, second.healthThresholdPercent());
        assertEquals("BANDIT_LEADER", content.quests().require("FIRST_DISCIPLE_PROMOTION_TRIAL").targetId().orElseThrow());
    }

    @Test
    void rejectsMissingQuestRankWithConfigurationPath() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.quests(), Files.readString(paths.quests())
                .replace("required-rank-id: SECOND_GENERATION_DISCIPLE", "required-rank-id: UNKNOWN_RANK"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));

        assertTrue(error.getMessage().contains("quests.yml.quests[id=FIRST_DISCIPLE_PROMOTION_TRIAL].required-rank-id"));
        assertTrue(error.getMessage().contains("UNKNOWN_RANK"));
    }

    @Test
    void rejectsDuplicateQuestId() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.quests(), Files.readString(paths.quests()) + """
                  - {id: LIUHE_ART_QUEST, display-name: duplicate, organization-id: HUASHAN_SECT,
                     objective-type: TRAINING, target-id: HUASHAN_TRAINING_DUMMY, required-hits: 1,
                     required-meditation-seconds: 1, meditation-mode: CUMULATIVE}
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));
        assertTrue(error.getMessage().contains("Duplicate quest ID: LIUHE_ART_QUEST"));
    }

    @Test
    void rejectsNegativeQuestGoalWithEntryPath() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.quests(), Files.readString(paths.quests())
                .replaceFirst("required-hits: 10", "required-hits: -1"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));
        assertTrue(error.getMessage().contains("quests.yml.quests[0]"));
        assertTrue(error.getMessage().contains("Negative quest goal"));
    }

    @Test
    void rejectsMissingAllianceWithConfigurationPath() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.organizations(), Files.readString(paths.organizations())
                .replace("alliance-id: MURIM_ALLIANCE", "alliance-id: MISSING_ALLIANCE"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));

        assertTrue(error.getMessage().contains("sects.yml.organizations[id=NAMGUNG_FAMILY].alliance-id"));
        assertTrue(error.getMessage().contains("MISSING_ALLIANCE"));
    }

    @Test
    void rejectsMissingRankSystemWithConfigurationPath() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.organizations(), Files.readString(paths.organizations())
                .replaceFirst("rank-system-id: FAMILY_RANKS", "rank-system-id: MISSING_RANKS"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));

        assertTrue(error.getMessage().contains("rank-system-id references missing MISSING_RANKS"));
    }

    @Test
    void rejectsDuplicateOrganizationId() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.organizations(), Files.readString(paths.organizations()) + """
                  - {id: NAMGUNG_FAMILY, display-name: duplicate, alliance-id: MURIM_ALLIANCE, category-id: FIVE_GREAT_FAMILIES, type: FAMILY}
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));

        assertTrue(error.getMessage().contains("Duplicate organization ID: NAMGUNG_FAMILY"));
    }

    @Test
    void rejectsInvalidExaminerThresholdWithEntryPath() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.quests(), Files.readString(paths.quests()).replace("threshold-percent: 50.0", "threshold-percent: 100.0"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));
        assertTrue(error.getMessage().contains("quests.yml.quests[2]"));
        assertTrue(error.getMessage().contains("threshold-percent"));
    }

    @Test
    void rejectsMissingPromotionMonsterReference() throws Exception {
        Paths paths = copiedResources();
        Files.writeString(paths.quests(), Files.readString(paths.quests()).replace("target-id: BANDIT_LEADER", "target-id: UNKNOWN_MONSTER"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> load(paths));
        assertTrue(error.getMessage().contains("quests.yml.quests[id=FIRST_DISCIPLE_PROMOTION_TRIAL].target-id"));
        assertTrue(error.getMessage().contains("UNKNOWN_MONSTER"));
    }

    private ContentCatalog loadResources() {
        return loader.load(Path.of("src/main/resources/factions.yml"), Path.of("src/main/resources/sects.yml"),
                Path.of("src/main/resources/ranks.yml"), Path.of("src/main/resources/skills.yml"),
                Path.of("src/main/resources/monsters.yml"), Path.of("src/main/resources/quests.yml"), progression);
    }

    private long count(FactionCatalog factions, OrganizationCategory category) {
        return factions.organizations().stream().filter(value -> value.category() == category).count();
    }

    private void assertRankIds(FactionCatalog factions, String systemId, List<String> expected) {
        assertEquals(expected, factions.findRankSystem(systemId).orElseThrow().ranks().stream()
                .map(OrganizationRankDefinition::id).toList());
    }

    private Paths copiedResources() throws Exception {
        Path factions = copy("factions.yml");
        Path organizations = copy("sects.yml");
        Path ranks = copy("ranks.yml");
        Path skills = copy("skills.yml");
        Path monsters = copy("monsters.yml");
        Path quests = copy("quests.yml");
        return new Paths(factions, organizations, ranks, skills, monsters, quests);
    }

    private Path copy(String name) throws Exception {
        return Files.copy(Path.of("src/main/resources", name), tempDirectory.resolve(name));
    }

    private ContentCatalog load(Paths paths) {
        return loader.load(paths.factions(), paths.organizations(), paths.ranks(), paths.skills(),
                paths.monsters(), paths.quests(), progression);
    }

    private record Paths(Path factions, Path organizations, Path ranks, Path skills, Path monsters, Path quests) {}
}
