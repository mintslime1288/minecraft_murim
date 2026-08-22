package com.murimrpg.command;

import com.murimrpg.domain.MajorRealm;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ProgressUpdate;
import com.murimrpg.domain.RealmPhase;
import com.murimrpg.presentation.StatusMessageFactory;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProfileLoadState;
import com.murimrpg.service.StatusService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private static final List<String> ROOT_COMMANDS = List.of("status", "profile", "admin");
    private static final List<String> ADMIN_COMMANDS = List.of("setprogress", "resetprogress");

    private final StatusService statusService;
    private final PlayerProfileService profileService;
    private final StatusMessageFactory messageFactory;
    private final Executor mainThreadExecutor;
    private final Logger logger;

    public MurimCommand(
            StatusService statusService,
            PlayerProfileService profileService,
            StatusMessageFactory messageFactory,
            Executor mainThreadExecutor,
            Logger logger
    ) {
        this.statusService = statusService;
        this.profileService = profileService;
        this.messageFactory = messageFactory;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 1 && "status".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("murimrpg.command.status")) {
                sender.sendMessage(messageFactory.error("권한이 없습니다: murimrpg.command.status"));
                return true;
            }
            messageFactory.status(statusService.status()).forEach(sender::sendMessage);
            return true;
        }
        if (args.length == 1 && "profile".equalsIgnoreCase(args[0])) {
            showProfile(sender);
            return true;
        }
        if (args.length >= 2 && "admin".equalsIgnoreCase(args[0])) {
            handleAdmin(sender, args);
            return true;
        }

        sender.sendMessage(messageFactory.usage(label));
        return true;
    }

    private void showProfile(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageFactory.error("이 명령은 플레이어만 사용할 수 있습니다."));
            return;
        }

        ProfileLoadState stateBeforeRequest = profileService.loadState(player.getUniqueId());
        if (stateBeforeRequest != ProfileLoadState.LOADED) {
            player.sendMessage(messageFactory.info("프로필 데이터를 불러오는 중입니다…"));
        }
        profileService.loadOrCreate(player.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(player, "프로필을 불러오지 못했습니다.", error);
                return;
            }
            messageFactory.profile(profile, profileService.loadState(player.getUniqueId()))
                    .forEach(player::sendMessage);
        }, mainThreadExecutor);
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("murim.admin")) {
            sender.sendMessage(messageFactory.error("권한이 없습니다: murim.admin"));
            return;
        }
        if (args.length == 6 && "setprogress".equalsIgnoreCase(args[1])) {
            setProgress(sender, args);
            return;
        }
        if (args.length == 3 && "resetprogress".equalsIgnoreCase(args[1])) {
            resetProgress(sender, args[2]);
            return;
        }
        sender.sendMessage(messageFactory.error(
                "사용법: /murim admin setprogress <player> <majorRealm> <phase> <exp>"
        ));
        sender.sendMessage(messageFactory.error("사용법: /murim admin resetprogress <player>"));
    }

    private void setProgress(CommandSender sender, String[] args) {
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(messageFactory.error("온라인 플레이어를 찾을 수 없습니다: " + args[2]));
            return;
        }

        Optional<MajorRealm> majorRealm = MajorRealm.fromId(args[3]);
        Optional<RealmPhase> phase = RealmPhase.fromId(args[4]);
        if (majorRealm.isEmpty() || phase.isEmpty()) {
            sender.sendMessage(messageFactory.error("알 수 없는 대경지 또는 소경지 ID입니다."));
            return;
        }

        long cultivationExp;
        try {
            cultivationExp = Long.parseLong(args[5]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messageFactory.error("수련 EXP는 정수여야 합니다."));
            return;
        }
        MajorRealm parsedMajorRealm = majorRealm.orElseThrow();
        RealmPhase parsedPhase = phase.orElseThrow();
        ProgressUpdate progressUpdate;
        try {
            progressUpdate = new ProgressUpdate(parsedMajorRealm, parsedPhase, cultivationExp);
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(messageFactory.error(exception.getMessage()));
            return;
        }

        sender.sendMessage(messageFactory.info(
                "[테스트 명령] " + target.getName() + "의 경지를 "
                        + parsedMajorRealm.id() + "/" + parsedPhase.id()
                        + ", EXP " + cultivationExp + "로 저장합니다."
        ));
        profileService.setProgress(
                target.getUniqueId(),
                progressUpdate
        ).whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(sender, "진행도를 저장하지 못했습니다.", error);
                return;
            }
            sender.sendMessage(messageFactory.success(
                    target.getName() + "의 테스트 진행도를 즉시 저장했습니다: " + summary(profile)
            ));
            if (target.isOnline()) {
                target.sendMessage(messageFactory.info(
                        "관리자가 테스트 진행도를 변경했습니다: " + summary(profile)
                ));
            }
        }, mainThreadExecutor);
    }

    private void resetProgress(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage(messageFactory.error("온라인 플레이어를 찾을 수 없습니다: " + playerName));
            return;
        }

        sender.sendMessage(messageFactory.info(
                "[테스트 명령·실행 전 확인] 대상: " + target.getName()
                        + ", 변경: 삼류·입문 / EXP 0 / 완료 조건 초기화 / 해금 콘텐츠 QI_BAR"
        ));
        profileService.resetProgress(target.getUniqueId()).whenCompleteAsync((profile, error) -> {
            if (error != null) {
                reportFailure(sender, "진행도를 초기화하지 못했습니다.", error);
                return;
            }
            sender.sendMessage(messageFactory.success(
                    target.getName() + "의 테스트 진행도를 초기화하고 즉시 저장했습니다."
            ));
            if (target.isOnline()) {
                target.sendMessage(messageFactory.info(
                        "관리자가 테스트 진행도를 초기화했습니다: " + summary(profile)
                ));
            }
        }, mainThreadExecutor);
    }

    private String summary(PlayerProfile profile) {
        return "[" + profile.realmDisplayName() + "], EXP " + profile.cultivationExp();
    }

    private void reportFailure(CommandSender sender, String userMessage, Throwable error) {
        Throwable cause = rootCause(error);
        logger.log(Level.SEVERE, userMessage, cause);
        sender.sendMessage(messageFactory.error(userMessage + " 서버 로그를 확인하세요."));
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
        if (args.length == 1) {
            return matches(ROOT_COMMANDS, args[0]);
        }
        if (!"admin".equalsIgnoreCase(args[0]) || !sender.hasPermission("murim.admin")) {
            return List.of();
        }
        if (args.length == 2) {
            return matches(ADMIN_COMMANDS, args[1]);
        }
        if (args.length == 3) {
            return matches(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[2]);
        }
        if ("setprogress".equalsIgnoreCase(args[1])) {
            if (args.length == 4) {
                return matches(Arrays.stream(MajorRealm.values()).map(MajorRealm::id).toList(), args[3]);
            }
            if (args.length == 5) {
                List<String> phases = MajorRealm.fromId(args[3])
                        .map(realm -> realm.phases().stream().map(RealmPhase::id).toList())
                        .orElseGet(() -> Arrays.stream(RealmPhase.values()).map(RealmPhase::id).toList());
                return matches(phases, args[4]);
            }
            if (args.length == 6) {
                return matches(List.of("0", "100"), args[5]);
            }
        }
        return List.of();
    }

    private List<String> matches(List<String> candidates, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
    }
}
