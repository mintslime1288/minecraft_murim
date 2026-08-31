package com.murimrpg.command;

import com.murimrpg.domain.ExperienceChange;
import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.domain.RequirementChange;
import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.Family;
import com.murimrpg.infrastructure.paper.PromotionFeedback;
import com.murimrpg.presentation.StatusMessageFactory;
import com.murimrpg.service.CachedProfilesSaveResult;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProfileLoadState;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.service.StatusService;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.ui.CharacterCreationGui;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MurimCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = List.of(
            "status", "profile", "realm", "cultivate", "sect", "quest", "skill", "qi", "admin"
    );
    private static final List<String> ADMIN_COMMANDS = List.of(
            "addexp", "setexp", "requirement", "stage", "testprogress",
            "setprogress", "resetprogress", "debugprofile", "saveprofiles", "character"
    );
    private static final List<String> REQUIREMENT_ACTIONS = List.of("grant", "revoke");
    private static final List<String> TEST_PROGRESS_ACTIONS = List.of("prepare", "clear");

    private final StatusService statusService;
    private final PlayerProfileService profileService;
    private final ProgressionService progressionService;
    private final StatusMessageFactory messageFactory;
    private final PromotionFeedback promotionFeedback;
    private final Executor mainThreadExecutor;
    private final Logger logger;
    private final CharacterCreationService characterCreationService;
    private final CharacterCreationGui characterGui;
    private final HuashanCommandHandler huashanCommands;

    public MurimCommand(
            StatusService statusService,
            PlayerProfileService profileService,
            ProgressionService progressionService,
            StatusMessageFactory messageFactory,
            PromotionFeedback promotionFeedback,
            Executor mainThreadExecutor,
            Logger logger,
            CharacterCreationService characterCreationService,
            CharacterCreationGui characterGui,
            HuashanCommandHandler huashanCommands
    ) {
        this.statusService = statusService;
        this.profileService = profileService;
        this.progressionService = progressionService;
        this.messageFactory = messageFactory;
        this.promotionFeedback = promotionFeedback;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
        this.characterCreationService = characterCreationService;
        this.characterGui = characterGui;
        this.huashanCommands = huashanCommands;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if ("gangho".equalsIgnoreCase(command.getName())) {
            return handleGangho(sender, args);
        }
        if (huashanCommands.handle(sender, args)) return true;
        if (args.length == 1 && "status".equalsIgnoreCase(args[0])) {
            showStatus(sender);
            return true;
        }
        if (args.length == 1 && "profile".equalsIgnoreCase(args[0])) {
            showProfile(sender);
            return true;
        }
        if (args.length == 1 && "realm".equalsIgnoreCase(args[0])) {
            showRealm(sender);
            return true;
        }
        if (args.length == 1 && "cultivate".equalsIgnoreCase(args[0])) {
            cultivate(sender);
            return true;
        }
        if (args.length >= 2 && "admin".equalsIgnoreCase(args[0])) {
            handleAdmin(sender, args);
            return true;
        }
        sender.sendMessage(messageFactory.usage(label));
        return true;
    }

    private boolean handleGangho(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(messageFactory.error("사용법: /강호 <스탯|가문|체질|리롤|초기화> [player]")); return true;
        }
        String action = ganghoAction(args[0]);
        if (action == null) {
            sender.sendMessage(messageFactory.error("알 수 없는 강호 명령입니다.")); return true;
        }
        String targetName = args.length == 2 ? args[1] : sender instanceof Player player ? player.getName() : null;
        if (targetName == null) { sender.sendMessage(messageFactory.error("콘솔에서는 플레이어 이름이 필요합니다.")); return true; }
        boolean changesCharacter = "reroll".equals(action) || "reset".equals(action);
        boolean targetsOtherPlayer = !(sender instanceof Player player) || !player.getName().equalsIgnoreCase(targetName);
        if ((changesCharacter || targetsOtherPlayer) && !sender.hasPermission("murim.admin")) { sender.sendMessage(messageFactory.error("권한이 없습니다: murim.admin")); return true; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { sender.sendMessage(messageFactory.error("온라인 플레이어를 찾을 수 없습니다: " + targetName)); return true; }
        if ("stats".equals(action)) { profileService.loadOrCreate(target.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) reportFailure(sender, "스탯 조회에 실패했습니다.", error); else sendCharacterStats(sender, target, profile.characterData());
        }, mainThreadExecutor); return true; }
        if ("family".equals(action) || "constitution".equals(action)) { profileService.loadOrCreate(target.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) { reportFailure(sender, "캐릭터 정보 조회에 실패했습니다.", error); return; }
            CharacterData data = profile.characterData();
            if (!data.completed()) { sender.sendMessage(messageFactory.info(target.getName() + ": 캐릭터 생성 미확정")); return; }
            String value = "family".equals(action) ? data.family().displayName() : data.constitution().displayName();
            sender.sendMessage(messageFactory.info(target.getName() + "의 " + ("family".equals(action) ? "가문" : "체질") + ": " + value));
        }, mainThreadExecutor); return true; }
        handleAdmin(sender, new String[] { "admin", "character", action, targetName });
        return true;
    }

    private String ganghoAction(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "stats", "stat", "스탯", "능력치" -> "stats";
            case "family", "가문" -> "family";
            case "constitution", "체질" -> "constitution";
            case "reroll", "리롤" -> "reroll";
            case "reset", "리셋", "초기화" -> "reset";
            default -> null;
        };
    }

    private void showStatus(CommandSender sender) {
        if (!sender.hasPermission("murimrpg.command.status")) {
            sender.sendMessage(messageFactory.error("권한이 없습니다: murimrpg.command.status"));
            return;
        }
        messageFactory.status(statusService.status()).forEach(sender::sendMessage);
    }

    private void showProfile(CommandSender sender) {
        Player player = playerSender(sender);
        if (player == null) {
            return;
        }
        ProfileLoadState state = profileService.loadState(player.getUniqueId());
        if (state != ProfileLoadState.LOADED) {
            player.sendMessage(messageFactory.info("프로필 데이터를 불러오는 중입니다…"));
        }
        profileService.loadOrCreate(player.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(player, "프로필을 불러오지 못했습니다.", error);
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            messageFactory.profile(
                            profile,
                            profileService.loadState(player.getUniqueId()),
                            statusService.status().dataStoreStatus()
                    )
                    .forEach(player::sendMessage);
        }, mainThreadExecutor);
    }

    private void showRealm(CommandSender sender) {
        Player player = playerSender(sender);
        if (player == null) {
            return;
        }
        progressionService.realm(player.getUniqueId()).whenCompleteAsync((view, error) -> {
            if (error != null) {
                reportFailure(player, "경지 정보를 불러오지 못했습니다.", error);
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            messageFactory.realm(view).forEach(player::sendMessage);
            player.sendActionBar(messageFactory.cultivationActionBar(view));
        }, mainThreadExecutor);
    }

    private void cultivate(CommandSender sender) {
        Player player = playerSender(sender);
        if (player == null) {
            return;
        }
        progressionService.promote(player.getUniqueId(), Bukkit.getCurrentTick()).whenCompleteAsync((result, error) -> {
            if (error != null) {
                reportFailure(player, "승급 처리에 실패했습니다.", error);
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            if (result.status() == PromotionResult.Status.SUCCESS) {
                promotionFeedback.showSuccess(player, result, messageFactory.promotion(result));
            } else {
                messageFactory.promotion(result).forEach(player::sendMessage);
            }
            showCultivationActionBar(player);
        }, mainThreadExecutor);
    }

    private Player playerSender(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(messageFactory.error("이 명령은 플레이어만 사용할 수 있습니다."));
        return null;
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("murim.admin")) {
            sender.sendMessage(messageFactory.error("권한이 없습니다: murim.admin"));
            return;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (java.util.Set.of("sect", "quest", "skill", "qi", "huashan").contains(subcommand)) {
            huashanCommands.handleAdmin(sender, args);
            return;
        }
        switch (subcommand) {
            case "addexp" -> addExp(sender, args);
            case "setexp" -> setExp(sender, args);
            case "requirement" -> requirement(sender, args);
            case "stage" -> adminStage(sender, args);
            case "testprogress" -> testProgress(sender, args);
            case "setprogress" -> setProgress(sender, args);
            case "resetprogress" -> resetProgress(sender, args);
            case "debugprofile" -> debugProfile(sender, args);
            case "saveprofiles" -> saveProfiles(sender, args);
            case "character" -> adminCharacter(sender, args);
            default -> adminUsage(sender);
        }
    }

    private void addExp(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin addexp <player> <amount>"));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        Long amount = parseLong(sender, args[3]);
        if (target == null || amount == null) {
            return;
        }
        progressionService.addExp(target.getUniqueId(), amount).whenCompleteAsync((change, error) -> {
            if (error != null) {
                reportFailure(sender, "EXP 추가에 실패했습니다.", error);
                return;
            }
            sendExperienceChange(sender, target, "추가", change);
        }, mainThreadExecutor);
    }

    private void setExp(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin setexp <player> <amount>"));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        Long amount = parseLong(sender, args[3]);
        if (target == null || amount == null) {
            return;
        }
        progressionService.setExp(target.getUniqueId(), amount).whenCompleteAsync((change, error) -> {
            if (error != null) {
                reportFailure(sender, "EXP 설정에 실패했습니다.", error);
                return;
            }
            sendExperienceChange(sender, target, "설정", change);
        }, mainThreadExecutor);
    }

    private void requirement(CommandSender sender, String[] args) {
        if (args.length != 5 || !REQUIREMENT_ACTIONS.contains(args[2].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(messageFactory.error(
                    "사용법: /murim admin requirement <grant|revoke> <player> <requirementId>"
            ));
            return;
        }
        Player target = onlinePlayer(sender, args[3]);
        if (target == null) {
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        String requirementId = args[4].toLowerCase(Locale.ROOT);
        CompletableFuture<RequirementChange> future;
        try {
            future = "grant".equals(action)
                    ? progressionService.grantRequirement(target.getUniqueId(), requirementId)
                    : progressionService.revokeRequirement(target.getUniqueId(), requirementId);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(messageFactory.error(exception.getMessage()));
            return;
        }
        future.whenCompleteAsync((change, error) -> {
            if (error != null) {
                reportFailure(sender, "승급 조건 변경에 실패했습니다.", error);
                return;
            }
            String verb = change.granted() ? "부여" : "제거";
            String changed = change.changed() ? "완료" : "변경 없음";
            String message = target.getName() + " 조건 " + change.requirementId() + " " + verb + ": " + changed;
            sender.sendMessage(messageFactory.success(message));
            notifyTarget(sender, target, message);
        }, mainThreadExecutor);
    }

    private void adminStage(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin stage <player>"));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        progressionService.realm(target.getUniqueId()).whenCompleteAsync((view, error) -> {
            if (error != null) {
                reportFailure(sender, "관리자 stage 조회에 실패했습니다.", error);
                return;
            }
            messageFactory.adminStage(view, profileService.loadState(target.getUniqueId()))
                    .forEach(sender::sendMessage);
        }, mainThreadExecutor);
    }

    private void testProgress(CommandSender sender, String[] args) {
        if (args.length != 4 || !TEST_PROGRESS_ACTIONS.contains(args[3].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(messageFactory.error(
                    "사용법: /murim admin testprogress <player> <prepare|clear>"
            ));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        boolean prepare = "prepare".equalsIgnoreCase(args[3]);
        long finalRequiredExp = progressionService.progression().finalStage().requiredCultivationExp();
        int requirementCount = progressionService.progression().requirementIds().size();
        sender.sendMessage(messageFactory.info(
                prepare
                        ? "[테스트 전용] 삼류·입문, EXP " + finalRequiredExp
                                + ", 전체 경계 조건 " + requirementCount
                                + "개를 준비합니다. 자동 승급하지 않습니다."
                        : "[테스트 전용] 삼류·입문, EXP 0, 조건 없음, 기본 해금으로 초기화합니다."
        ));
        CompletableFuture<PlayerProfile> future = prepare
                ? progressionService.prepareTestProgress(target.getUniqueId())
                : progressionService.clearTestProgress(target.getUniqueId());
        future.whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(sender, "테스트 진행도 변경에 실패했습니다.", error);
                return;
            }
            String message = target.getName() + " 테스트 진행도 " + (prepare ? "준비" : "초기화")
                    + " 완료: [" + profile.realmDisplayName() + "], EXP " + profile.cultivationExp();
            sender.sendMessage(messageFactory.success(message));
            notifyTarget(sender, target, message);
        }, mainThreadExecutor);
    }

    private void setProgress(CommandSender sender, String[] args) {
        if (args.length != 6) {
            sender.sendMessage(messageFactory.error(
                    "사용법: /murim admin setprogress <player> <majorRealm> <phase> <exp>"
            ));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        Optional<MajorRealm> majorRealm = MajorRealm.fromId(args[3]);
        Optional<RealmPhase> phase = RealmPhase.fromId(args[4]);
        Long exp = parseLong(sender, args[5]);
        if (target == null || majorRealm.isEmpty() || phase.isEmpty() || exp == null) {
            if (majorRealm.isEmpty() || phase.isEmpty()) {
                sender.sendMessage(messageFactory.error("알 수 없는 대경지 또는 소경지 ID입니다."));
            }
            return;
        }
        ProgressUpdate update;
        try {
            update = new ProgressUpdate(majorRealm.orElseThrow(), phase.orElseThrow(), exp);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(messageFactory.error(exception.getMessage()));
            return;
        }
        progressionService.setProgressForTesting(target.getUniqueId(), update)
                .whenCompleteAsync((profile, error) -> {
                    if (error != null) {
                        reportFailure(sender, "진행도 저장에 실패했습니다.", error);
                        return;
                    }
                    String message = target.getName() + " 테스트 진행도 저장: ["
                            + profile.realmDisplayName() + "], EXP " + profile.cultivationExp();
                    sender.sendMessage(messageFactory.success(message));
                    notifyTarget(sender, target, message);
                }, mainThreadExecutor);
    }

    private void resetProgress(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin resetprogress <player>"));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        sender.sendMessage(messageFactory.info(
                "[테스트 명령·실행 전 확인] 대상: " + target.getName()
                        + ", 변경: 삼류·입문 / EXP 0 / 완료 조건 초기화 / 기본 해금"
        ));
        progressionService.clearTestProgress(target.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(sender, "진행도 초기화에 실패했습니다.", error);
                return;
            }
            String message = target.getName() + " 테스트 진행도를 초기화하고 즉시 저장했습니다.";
            sender.sendMessage(messageFactory.success(message));
            notifyTarget(sender, target, message);
        }, mainThreadExecutor);
    }

    private void debugProfile(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin debugprofile <player>"));
            return;
        }
        Player target = onlinePlayer(sender, args[2]);
        if (target == null) {
            return;
        }
        boolean cacheExisted = profileService.cachedProfile(target.getUniqueId()).isPresent();
        progressionService.realm(target.getUniqueId()).whenCompleteAsync((view, error) -> {
            if (error != null) {
                reportFailure(sender, "프로필 디버그 조회에 실패했습니다.", error);
                return;
            }
            messageFactory.debugProfile(
                            view,
                            cacheExisted,
                            profileService.loadState(target.getUniqueId())
                    )
                    .forEach(sender::sendMessage);
        }, mainThreadExecutor);
    }

    private void saveProfiles(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin saveprofiles"));
            return;
        }
        sender.sendMessage(messageFactory.info("캐시된 프로필 저장을 비동기로 요청했습니다."));
        profileService.saveCachedProfiles().whenCompleteAsync((result, error) -> {
            if (error != null) {
                reportFailure(sender, "캐시 프로필 저장에 실패했습니다.", error);
                return;
            }
            reportManualSaveFailures(result);
            String summary = "프로필 저장 완료: 성공 " + result.successCount()
                    + "개, 실패 " + result.failureCount() + "개";
            sender.sendMessage(result.failureCount() == 0
                    ? messageFactory.success(summary)
                    : messageFactory.error(summary));
        }, mainThreadExecutor);
    }

    private void reportManualSaveFailures(CachedProfilesSaveResult result) {
        result.failures().forEach((uuid, failure) -> logger.log(
                Level.SEVERE,
                "Manual cached profile save failed for " + uuid,
                failure
        ));
    }

    private void showCultivationActionBar(Player player) {
        progressionService.realm(player.getUniqueId()).whenCompleteAsync((view, error) -> {
            if (error != null) {
                logger.log(Level.WARNING, "Could not prepare cultivation action bar for " + player.getUniqueId(),
                        rootCause(error));
                return;
            }
            if (player.isOnline()) {
                player.sendActionBar(messageFactory.cultivationActionBar(view));
            }
        }, mainThreadExecutor);
    }

    private void sendExperienceChange(
            CommandSender sender,
            Player target,
            String action,
            ExperienceChange change
    ) {
        String message = target.getName() + " EXP " + action + ": " + change.before() + " → " + change.after();
        sender.sendMessage(messageFactory.success(message));
        notifyTarget(sender, target, message);
    }

    private void notifyTarget(CommandSender sender, Player target, String message) {
        if (target.isOnline() && sender != target) {
            target.sendMessage(messageFactory.info("관리자 테스트 변경: " + message));
        }
    }

    private Player onlinePlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(messageFactory.error("온라인 플레이어를 찾을 수 없습니다: " + playerName));
        }
        return target;
    }

    private Long parseLong(CommandSender sender, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messageFactory.error("값은 정수여야 합니다: " + value));
            return null;
        }
    }

    private void adminUsage(CommandSender sender) {
        sender.sendMessage(messageFactory.error(
                "관리자 명령: addexp, setexp, requirement, stage, testprogress, setprogress, "
                        + "resetprogress, debugprofile, saveprofiles"
                        + ", character"
        ));
    }

    private void reportFailure(CommandSender sender, String fallback, Throwable error) {
        Throwable cause = rootCause(error);
        logger.log(Level.SEVERE, fallback, cause);
        String message = cause instanceof IllegalArgumentException && cause.getMessage() != null
                ? cause.getMessage()
                : fallback + " 서버 로그를 확인하세요.";
        sender.sendMessage(messageFactory.error(message));
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if ("gangho".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) return matches(List.of("스탯", "가문", "체질", "리롤", "초기화"), args[0]);
            if (args.length == 2 && sender.hasPermission("murim.admin")) return onlinePlayers(args[1]);
            return List.of();
        }
        if (args.length == 1) {
            return matches(ROOT_COMMANDS, args[0]);
        }
        if (!"admin".equalsIgnoreCase(args[0]) || !sender.hasPermission("murim.admin")) {
            return List.of();
        }
        if (args.length == 2) {
            return matches(ADMIN_COMMANDS, args[1]);
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if ("requirement".equals(subcommand)) {
                return matches(REQUIREMENT_ACTIONS, args[2]);
            }
            if ("character".equals(subcommand)) {
                return matches(List.of("stats", "reroll", "reset"), args[2]);
            }
            if ("saveprofiles".equals(subcommand)) {
                return List.of();
            }
            return onlinePlayers(args[2]);
        }
        if (args.length == 4) {
            return switch (subcommand) {
                case "requirement" -> onlinePlayers(args[3]);
                case "testprogress" -> matches(TEST_PROGRESS_ACTIONS, args[3]);
                case "setprogress" -> matches(
                        Arrays.stream(MajorRealm.values()).map(MajorRealm::id).toList(),
                        args[3]
                );
                case "addexp" -> matches(cultivationExpSuggestions(false), args[3]);
                case "setexp" -> matches(cultivationExpSuggestions(true), args[3]);
                case "character" -> onlinePlayers(args[3]);
                default -> List.of();
            };
        }
        if (args.length == 5) {
            if ("requirement".equals(subcommand)) {
                return matches(progressionService.progression().requirementIds().stream().sorted().toList(), args[4]);
            }
            if ("setprogress".equals(subcommand)) {
                List<String> phases = MajorRealm.fromId(args[3])
                        .map(realm -> realm.phases().stream().map(RealmPhase::id).toList())
                        .orElseGet(() -> Arrays.stream(RealmPhase.values()).map(RealmPhase::id).toList());
                return matches(phases, args[4]);
            }
        }
        if (args.length == 6 && "setprogress".equals(subcommand)) {
            return matches(cultivationExpSuggestions(true), args[5]);
        }
        return List.of();
    }

    private List<String> onlinePlayers(String input) {
        return matches(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), input);
    }

    private void adminCharacter(CommandSender sender, String[] args) {
        if (args.length != 4 || !("stats".equalsIgnoreCase(args[2]) || "reset".equalsIgnoreCase(args[2]) || "reroll".equalsIgnoreCase(args[2]))) {
            sender.sendMessage(messageFactory.error("사용법: /murim admin character <stats|reset|reroll> <player>")); return;
        }
        Player target = onlinePlayer(sender, args[3]); if (target == null) return;
        String action = args[2].toLowerCase(Locale.ROOT);
        if ("stats".equals(action)) { profileService.loadOrCreate(target.getUniqueId()).whenCompleteAsync((p,e) -> { if(e != null) reportFailure(sender,"캐릭터 조회에 실패했습니다.",e); else sendCharacterStats(sender,target,p.characterData()); }, mainThreadExecutor); return; }
        if ("reset".equals(action)) { characterCreationService.reset(target.getUniqueId()).whenCompleteAsync((p,e) -> { if(e != null) reportFailure(sender,"캐릭터 초기화에 실패했습니다.",e); else { sender.sendMessage(messageFactory.success(target.getName()+"의 최초 생성 상태를 초기화했습니다.")); characterGui.openIfNeeded(target); } }, mainThreadExecutor); return; }
        characterCreationService.reroll(target.getUniqueId()).whenCompleteAsync((d,e) -> { if(e != null) reportFailure(sender,"캐릭터 리롤에 실패했습니다.",e); else { sender.sendMessage(messageFactory.success(target.getName()+"의 드래프트를 다시 뽑았습니다.")); characterGui.openIfNeeded(target); } }, mainThreadExecutor);
    }

    private void sendCharacterStats(CommandSender sender, Player target, CharacterData data) {
        if (!data.completed()) { sender.sendMessage(messageFactory.info(target.getName()+": 캐릭터 생성 미확정, 리롤 "+data.rerollsUsed()+"회 사용")); return; }
        int strength = data.baseStrength() + (data.family() == Family.STREET ? 0 : characterCreationService.settings().strengthBonuses().get(data.family()));
        int speed = data.baseSpeed() + (data.family() == Family.STREET ? 0 : characterCreationService.settings().speedBonuses().get(data.family()));
        sender.sendMessage(messageFactory.info(target.getName()+": "+data.family().displayName()+", 재능 "+(int)(data.talentMultiplier()*100)+"%, 힘 "+strength+", 속도 "+speed+", "+data.constitution().displayName()));
    }

    private List<String> cultivationExpSuggestions(boolean includeZero) {
        return progressionService.progression().stages().stream()
                .map(stage -> stage.requiredCultivationExp())
                .filter(value -> includeZero || value > 0)
                .distinct()
                .map(String::valueOf)
                .toList();
    }

    private List<String> matches(List<String> candidates, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
