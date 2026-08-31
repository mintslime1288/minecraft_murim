package com.murimrpg.command;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.QuestDefinition;
import com.murimrpg.infrastructure.paper.HuashanTrainingDummy;
import com.murimrpg.infrastructure.paper.HuashanExamEntities;
import com.murimrpg.infrastructure.paper.HuashanTrialSpawner;
import com.murimrpg.service.HuashanResult;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.OrganizationService;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HuashanCommandHandler {
    private final ContentCatalog catalog;
    private final PlayerContentService states;
    private final PlayerProfileService profiles;
    private final CultivationProgression progression;
    private final HuashanService service;
    private final HuashanTrainingDummy dummy;
    private final HuashanExamEntities examEntities;
    private final HuashanTrialSpawner trialSpawner;
    private final Executor mainThread;
    private final Logger logger;
    private final OrganizationService organizations;

    public HuashanCommandHandler(ContentCatalog catalog, PlayerContentService states,
            PlayerProfileService profiles, CultivationProgression progression, HuashanService service,
            HuashanTrainingDummy dummy, HuashanExamEntities examEntities,
            HuashanTrialSpawner trialSpawner, OrganizationService organizations, Executor mainThread, Logger logger) {
        this.catalog = catalog; this.states = states; this.profiles = profiles; this.progression = progression;
        this.service = service; this.dummy = dummy; this.examEntities = examEntities; this.trialSpawner = trialSpawner; this.mainThread = mainThread; this.logger = logger;
        this.organizations = organizations;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) return false;
        String root = args[0].toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("sect", "quest", "skill", "qi").contains(root)) return false;
        if (!(sender instanceof Player player)) {
            send(sender, "이 명령은 플레이어만 사용할 수 있습니다."); return true;
        }
        switch (root) {
            case "sect" -> sect(player, args);
            case "quest" -> {
                if (args.length == 2 && "status".equalsIgnoreCase(args[1])) showQuest(player);
                else send(player, "사용법: /murim quest status");
            }
            case "skill" -> skill(player, args);
            case "qi" -> {
                if (args.length == 1) showQi(player); else send(player, "사용법: /murim qi");
            }
            default -> throw new IllegalStateException("Unexpected command root: " + root);
        }
        return true;
    }

    public void handleAdmin(CommandSender sender, String[] args) {
        String root = args[1].toLowerCase(Locale.ROOT);
        if ("huashan".equals(root)) {
            if (args.length == 3 && "spawnedummy".equalsIgnoreCase(args[2]) && sender instanceof Player player) {
                dummy.spawn(player); send(sender, catalog.huashanSettings().trainingDummyDisplayName() + "을 생성했습니다."); return;
            }
            if (args.length == 3 && "removedummy".equalsIgnoreCase(args[2])) {
                send(sender, catalog.huashanSettings().trainingDummyDisplayName() + " " + dummy.removeAll() + "개를 제거했습니다."); return;
            }
            if (args.length == 4 && "spawnexaminer".equalsIgnoreCase(args[2])) {
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) { send(sender, "온라인 플레이어를 찾을 수 없습니다: " + args[3]); return; }
                examEntities.spawnExaminer(target); send(sender, "승급 시험관을 생성했습니다: " + target.getName()); return;
            }
            if (args.length == 4 && "spawnleader".equalsIgnoreCase(args[2])) {
                Player target = Bukkit.getPlayerExact(args[3]);
                if (target == null) { send(sender, "온라인 플레이어를 찾을 수 없습니다: " + args[3]); return; }
                examEntities.spawnBanditLeader(target); send(sender, "산적 대장을 생성했습니다: " + target.getName()); return;
            }
            if (args.length == 3 && "removeexamentities".equalsIgnoreCase(args[2])) {
                send(sender, "시험 엔티티 " + examEntities.removeAll() + "개를 제거했습니다."); return;
            }
        }
        if (args.length < 4) { usage(sender); return; }
        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) { send(sender, "온라인 플레이어를 찾을 수 없습니다: " + args[3]); return; }
        CompletableFuture<HuashanResult> future;
        try {
            future = switch (root) {
                case "sect" -> adminSect(args, target);
                case "quest" -> adminQuest(sender, args, target);
                case "skill" -> adminSkill(sender, args, target);
                case "qi" -> adminQi(args, target);
                default -> null;
            };
        } catch (RuntimeException error) { send(sender, error.getMessage()); return; }
        if (future == null) { usage(sender); return; }
        future.whenCompleteAsync((result, error) -> complete(sender, target, result, error), mainThread);
    }

    private void sect(Player player, String[] args) {
        if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
            send(player, "현재 공개 조직:");
            organizations.visibleOrganizations().forEach(value -> send(player,
                    "- " + value.displayName() + " (" + value.id() + ")"
                            + (value.id().equals(catalog.huashanSettings().organizationId()) ? " [입문 가능]" : " [준비 중]")));
            return;
        }
        if (args.length == 3 && "info".equalsIgnoreCase(args[1])) {
            try {
                var value = organizations.requireVisible(args[2].toUpperCase(Locale.ROOT));
                send(player, value.id() + " - " + value.displayName());
            } catch (IllegalArgumentException error) {
                send(player, error.getMessage());
            }
            return;
        }
        if (args.length == 3 && "apply".equalsIgnoreCase(args[1])) {
            service.apply(player.getUniqueId(), args[2].toUpperCase(Locale.ROOT))
                    .whenCompleteAsync((result, error) -> trialStarted(player, result, error), mainThread); return;
        }
        if (args.length == 2 && "promote".equalsIgnoreCase(args[1])) {
            service.startPromotionTrial(player.getUniqueId())
                    .whenCompleteAsync((result, error) -> trialStarted(player, result, error), mainThread); return;
        }
        if (args.length == 2 && "status".equalsIgnoreCase(args[1])) { showState(player, player.getUniqueId()); return; }
        send(player, "사용법: /murim sect <list|info ID|apply ID|status|promote>");
    }

    private void skill(Player player, String[] args) {
        if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
            states.loadOrCreate(player.getUniqueId()).whenCompleteAsync((state, error) ->
                    send(player, error == null ? "습득 기술: " + state.content().learnedSkillIds() : root(error).getMessage()), mainThread);
            return;
        }
        if (args.length == 3 && "equip".equalsIgnoreCase(args[1])) {
            service.equip(player.getUniqueId(), args[2].toUpperCase(Locale.ROOT))
                    .whenCompleteAsync((result, error) -> complete(player, player, result, error), mainThread); return;
        }
        send(player, "사용법: /murim skill <list|equip ID>");
    }

    private void showQuest(Player player) { showState(player, player.getUniqueId()); }

    private void showState(CommandSender recipient, java.util.UUID uuid) {
        states.loadOrCreate(uuid).whenCompleteAsync((state, error) -> {
            if (error != null) { send(recipient, root(error).getMessage()); return; }
            send(recipient, "조직=" + state.content().organizationId().orElse("NONE")
                    + ", 계급=" + state.content().organizationRankId().orElse("NONE"));
            if (state.journey().activeActivityId().isEmpty()) { send(recipient, "진행 중 활동 없음"); return; }
            QuestDefinition quest = catalog.quests().require(state.journey().activeActivityId().orElseThrow());
            send(recipient, quest.displayName() + " [" + quest.objectiveType() + "]"
                    + (quest.objectiveType() == com.murimrpg.domain.QuestObjectiveType.TRAINING
                    ? ": 표적 " + state.journey().dummyHits() + "/" + quest.requiredHits()
                    + ", 운기조식 " + state.journey().meditationSeconds() + "/" + quest.requiredMeditationSeconds() + "초"
                    : ": 대상 " + quest.targetId().orElse("NONE")));
        }, mainThread);
    }

    private void showQi(Player player) {
        CompletableFuture.allOf(states.loadOrCreate(player.getUniqueId()), profiles.loadOrCreate(player.getUniqueId()))
                .whenCompleteAsync((ignored, error) -> {
                    if (error != null) { send(player, root(error).getMessage()); return; }
                    PlayerJourney state = states.cached(player.getUniqueId()).orElseThrow();
                    PlayerProfile profile = profiles.cachedProfile(player.getUniqueId()).orElseThrow();
                    long max = progression.stage(profile).maxQi();
                    send(player, "내공 " + state.journey().currentQi() + " / " + max
                            + ", 활성 심법=" + state.journey().activeSkillId().orElse("NONE"));
                }, mainThread);
    }

    private CompletableFuture<HuashanResult> adminSect(String[] args, Player target) {
        if (args.length == 6 && "setrank".equalsIgnoreCase(args[2]))
            return service.setRankForTest(target.getUniqueId(), args[4].toUpperCase(Locale.ROOT), args[5].toUpperCase(Locale.ROOT));
        if (args.length == 4 && "clear".equalsIgnoreCase(args[2])) return service.clear(target.getUniqueId());
        return null;
    }
    private CompletableFuture<HuashanResult> adminQuest(CommandSender sender, String[] args, Player target) {
        if (args.length == 5 && "complete".equalsIgnoreCase(args[2]))
            return service.completeForTest(target.getUniqueId(), args[4].toUpperCase(Locale.ROOT));
        if (args.length == 4 && "debug".equalsIgnoreCase(args[2])) {
            showState(sender, target.getUniqueId());
            return states.loadOrCreate(target.getUniqueId()).thenApply(state -> new HuashanResult(
                    state, "퀘스트 상태를 관리자에게 표시했습니다.", false));
        }
        return null;
    }
    private CompletableFuture<HuashanResult> adminSkill(CommandSender sender, String[] args, Player target) {
        if (args.length == 5 && "grant".equalsIgnoreCase(args[2])) return service.grantSkill(target.getUniqueId(), args[4].toUpperCase(Locale.ROOT));
        if (args.length == 5 && "revoke".equalsIgnoreCase(args[2])) return service.revokeSkill(target.getUniqueId(), args[4].toUpperCase(Locale.ROOT));
        if (args.length == 4 && "debug".equalsIgnoreCase(args[2])) {
            showState(sender, target.getUniqueId());
            return states.loadOrCreate(target.getUniqueId()).thenApply(state -> new HuashanResult(
                    state, "기술 상태를 관리자에게 표시했습니다.", false));
        }
        return null;
    }
    private CompletableFuture<HuashanResult> adminQi(String[] args, Player target) {
        if (args.length != 5 || !"set".equalsIgnoreCase(args[2])) return null;
        long amount = Long.parseLong(args[4]);
        PlayerProfile profile = profiles.cachedProfile(target.getUniqueId()).orElseThrow(() -> new IllegalStateException("프로필 로드 중입니다."));
        return service.setQi(target.getUniqueId(), amount, progression.stage(profile).maxQi());
    }

    private void complete(CommandSender sender, Player target, HuashanResult result, Throwable error) {
        if (error != null) { logger.log(Level.WARNING, "Huashan command failed", root(error)); send(sender, "처리 실패: " + root(error).getMessage()); }
        else send(sender, target.getName() + ": " + result.message());
    }
    private void trialStarted(Player player, HuashanResult result, Throwable error) {
        if (error != null) { complete(player, player, result, error); return; }
        send(player, result.message());
        trialSpawner.schedule(player, result.state()).ifPresent(target -> send(player,
                catalog.huashanSettings().trialSpawnDelaySeconds() + "초 후 " + target + "이(가) 소환됩니다."));
    }
    private void usage(CommandSender sender) { send(sender, "사용법: /murim admin <sect|quest|skill|qi|huashan> ..."); }
    private void send(CommandSender sender, String value) { sender.sendMessage(Component.text(value)); }
    private Throwable root(Throwable value) { while (value.getCause() != null) value = value.getCause(); return value; }
}
