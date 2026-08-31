package com.murimrpg.infrastructure.paper;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.QuestDefinition;
import com.murimrpg.service.PlayerContentService;
import java.util.Optional;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

/** Schedules Paper-only trial target spawning after a domain service starts an activity. */
public final class HuashanTrialSpawner {
    private final JavaPlugin plugin;
    private final ContentCatalog catalog;
    private final PlayerContentService states;
    private final HuashanTrainingDummy dummy;
    private final HuashanExamEntities examEntities;

    public HuashanTrialSpawner(JavaPlugin plugin, ContentCatalog catalog, PlayerContentService states,
            HuashanTrainingDummy dummy, HuashanExamEntities examEntities) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.states = states;
        this.dummy = dummy;
        this.examEntities = examEntities;
    }

    public Optional<String> schedule(Player player, PlayerJourney started) {
        Optional<QuestDefinition> quest = started.journey().activeActivityId().flatMap(catalog.quests()::find);
        if (quest.isEmpty() || targetName(quest.orElseThrow()).isEmpty()) return Optional.empty();
        String activityId = quest.orElseThrow().id();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            boolean stillActive = states.cached(player.getUniqueId())
                    .flatMap(value -> value.journey().activeActivityId()).filter(activityId::equals).isPresent();
            if (!stillActive) return;
            spawn(player, quest.orElseThrow());
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    targetName(quest.orElseThrow()).orElseThrow() + "이(가) 소환되었습니다."));
        }, catalog.huashanSettings().trialSpawnDelaySeconds() * 20L);
        return targetName(quest.orElseThrow());
    }

    private void spawn(Player player, QuestDefinition quest) {
        String target = quest.targetId().orElseThrow();
        if (target.equals(catalog.huashanSettings().trainingDummyId())) dummy.spawn(player);
        else if (target.equals(catalog.huashanSettings().examinerId())) examEntities.spawnExaminer(player);
        else if (target.equals("BANDIT_LEADER")) examEntities.spawnBanditLeader(player);
    }

    private Optional<String> targetName(QuestDefinition quest) {
        String target = quest.targetId().orElse("");
        if (target.equals(catalog.huashanSettings().trainingDummyId())) {
            return Optional.of(catalog.huashanSettings().trainingDummyDisplayName());
        }
        if (target.equals(catalog.huashanSettings().examinerId())) {
            return Optional.of(catalog.huashanSettings().examinerDisplayName());
        }
        return catalog.findMonster(target).map(value -> value.displayName());
    }
}
