package com.murimrpg.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.murimrpg.GanghoContext;
import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.Family;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmProgressView;
import com.murimrpg.domain.CurrencyType;
import com.murimrpg.service.InventoryCurrencyService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/** Brigadier is only an input adapter; all rules stay in services. */
public final class CommandRegistry {
    private final GanghoContext context;
    public CommandRegistry(GanghoContext context) { this.context = context; }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            dispatcher.register(root("gangho"));
            dispatcher.register(root("강호"));
            dispatcher.register(root("murim"));
            dispatcher.register(literal("내정보").executes(this::openProfile));
            dispatcher.register(literal("경지").executes(this::realm));
            dispatcher.register(literal("문파").executes(this::openSect));
            dispatcher.register(literal("화폐").executes(this::currency));
            dispatcher.register(literal("money").executes(this::currency));
            dispatcher.register(currencyAdmin());
            dispatcher.register(trainingItemAdmin());
            dispatcher.register(literal("메뉴").executes(this::menu));
            dispatcher.register(literal("menu").executes(this::menu));
            dispatcher.register(literal("무공").executes(this::openMartialArts));
            dispatcher.register(literal("내공").executes(this::qi));
            dispatcher.register(literal("운기조식").executes(this::meditate));
            for (String alias : java.util.List.of("퀘스트", "기연", "도감", "지도", "지역", "길찾기")) {
                dispatcher.register(literal(alias).executes(this::planned));
            }
        });
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> currencyAdmin() {
        return literal("화폐관리").requires(this::isOperator)
                .then(currencyTransferBranch("지급", true))
                .then(currencyTransferBranch("회수", false))
                .then(literal("확인").then(argument("대상", EntityArgument.player()).executes(this::currencyCheck)));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> trainingItemAdmin() {
        return literal("아이템관리").requires(this::isOperator)
                .then(literal("지급").then(argument("대상", EntityArgument.player())
                        .then(trainingItemLiteral("목검", com.murimrpg.service.TrainingItemService.WOODEN_SWORD))
                        .then(trainingItemLiteral("훈련허수아비", com.murimrpg.service.TrainingItemService.TRAINING_DUMMY))
                        .then(literal("훈련").then(trainingItemLiteral("허수아비", com.murimrpg.service.TrainingItemService.TRAINING_DUMMY)))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> trainingItemLiteral(String name, String id) {
        return literal(name).then(argument("수량", IntegerArgumentType.integer(1)).executes(command -> {
            ServerPlayer target = EntityArgument.getPlayer(command, "대상"); int count = IntegerArgumentType.getInteger(command, "수량");
            if (!context.trainingItems().give(target, id, count)) {
                command.getSource().sendFailure(Component.literal("[아이템관리] 대상 인벤토리에 빈 공간이 부족합니다.")); return 0;
            }
            send(command.getSource(), "[아이템관리] " + target.getName().getString() + "에게 " + name + " " + count + "개를 지급했습니다."); return 1;
        }));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> currencyTransferBranch(String action, boolean give) {
        var target = argument("대상", EntityArgument.player());
        for (CurrencyType type : CurrencyType.values()) {
            target.then(currencyLiteral(type, context.currencies().catalog().definition(type).displayName(), give));
            target.then(currencyLiteral(type, type.name(), give));
            for (String legacyId : type.legacyIds()) target.then(currencyLiteral(type, legacyId, give));
        }
        return literal(action).then(target);
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> currencyLiteral(CurrencyType type, String input, boolean give) {
        return literal(input).then(argument("수량", IntegerArgumentType.integer(1))
                .executes(command -> currencyTransfer(command, type, give)));
    }

    private int currency(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        return currencyMessage(command.getSource(), player);
    }

    private int currencyCheck(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return currencyMessage(command.getSource(), EntityArgument.getPlayer(command, "대상"));
    }

    private int currencyMessage(CommandSourceStack source, ServerPlayer player) {
        var balances = context.currencies().balances(player);
        String summary = balances.entrySet().stream().map(entry -> context.currencies().catalog().definition(entry.getKey()).displayName() + " " + entry.getValue() + "개")
                .reduce((left, right) -> left + " · " + right).orElse("없음");
        send(source, "[화폐] " + player.getName().getString() + " · " + summary + " · 총 가치 " + context.currencies().totalModernValueWon(player) + "원");
        return 1;
    }

    private int currencyTransfer(CommandContext<CommandSourceStack> command, CurrencyType type, boolean give) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(command, "대상");
        int amount = IntegerArgumentType.getInteger(command, "수량");
        InventoryCurrencyService.TransferResult result = give ? context.currencies().give(target, type, amount) : context.currencies().take(target, type, amount);
        String display = context.currencies().catalog().definition(type).displayName();
        if (result == InventoryCurrencyService.TransferResult.SUCCESS) {
            send(command.getSource(), "[화폐관리] " + target.getName().getString() + "에게 " + display + " " + amount + "개를 " + (give ? "지급했습니다." : "회수했습니다.")); return 1;
        }
        String error = switch (result) { case NO_SPACE -> "인벤토리 공간이 부족합니다."; case INSUFFICIENT -> "대상 플레이어의 화폐가 부족합니다."; case INVALID_AMOUNT -> "수량은 1 이상이어야 합니다."; default -> "처리에 실패했습니다."; };
        command.getSource().sendFailure(Component.literal("[화폐관리] " + error)); return 0;
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return literal(name).executes(this::openProfile)
                .then(literal("help").executes(this::help)).then(literal("도움말").executes(this::help))
                .then(literal("info").executes(this::openProfile)).then(literal("내정보").executes(this::openProfile))
                .then(literal("status").executes(this::profile))
                .then(literal("realm").executes(this::realm)).then(literal("경지").executes(this::realm))
                .then(literal("cultivate").executes(this::cultivate)).then(literal("돌파").executes(this::cultivate))
                .then(literal("sect").executes(this::openSect)
                        .then(literal("list").executes(this::sectList))
                        .then(literal("join").then(literal("huashan").executes(this::joinHuashan)))
                        .then(literal("가입").then(literal("화산파").executes(this::joinHuashan)))
                        .then(literal("promote").executes(this::sectPromote)))
                .then(literal("문파").executes(this::openSect).then(literal("목록").executes(this::sectList))
                        .then(literal("가입").then(literal("화산파").executes(this::joinHuashan)))
                        .then(literal("승급").executes(this::sectPromote)))
                .then(literal("martialarts").executes(this::openMartialArts)).then(literal("무공").executes(this::openMartialArts))
                .then(literal("qi").executes(this::qi)).then(literal("내공").executes(this::qi))
                .then(literal("meditate").executes(this::meditate)).then(literal("운기조식").executes(this::meditate))
                .then(characterBranch("character", "view", "reroll", "confirm"))
                .then(characterBranch("캐릭터", "보기", "리롤", "확정"))
                .then(literal("quest").executes(this::planned)).then(literal("퀘스트").executes(this::planned))
                .then(literal("encounter").executes(this::planned)).then(literal("기연").executes(this::planned))
                .then(literal("codex").executes(this::planned)).then(literal("도감").executes(this::planned))
                .then(literal("map").executes(this::planned)).then(literal("지도").executes(this::planned))
                .then(literal("region").executes(this::planned)).then(literal("지역").executes(this::planned))
                .then(literal("route").executes(this::planned)).then(literal("길찾기").executes(this::planned))
                .then(literal("admin").requires(this::isOperator)
                        .then(literal("save").executes(this::save))
                        .then(literal("testprogress")
                                .then(literal("prepare").executes(this::prepareTestProgress))
                                .then(literal("clear").executes(this::clearTestProgress)))
                        .then(requirementBranch())
                        .then(literal("addexp").then(argument("amount", LongArgumentType.longArg(1)).executes(this::addExp))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> requirementBranch() {
        var grant = literal("grant");
        var revoke = literal("revoke");
        for (String id : context.progression().requirementIds().stream().sorted().toList()) {
            grant.then(literal(id).executes(command -> requirement(command, id, true)));
            revoke.then(literal(id).executes(command -> requirement(command, id, false)));
        }
        return literal("requirement").then(grant).then(revoke);
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> characterBranch(
            String rootName, String viewName, String rerollName, String confirmName) {
        var confirm = literal(confirmName);
        for (Family family : Family.values()) confirm.then(literal(family.id()).executes(command -> confirm(command, family)));
        return literal(rootName).then(literal(viewName).executes(this::character))
                .then(literal(rerollName).executes(this::reroll)).then(confirm);
    }

    private int planned(CommandContext<CommandSourceStack> command) {
        send(command.getSource(), "[강호] 이 콘텐츠는 기존 프로젝트에서도 구현 준비 중입니다.");
        return 1;
    }

    private int help(CommandContext<CommandSourceStack> command) {
        send(command.getSource(), "[강호] /강호 <내정보|경지|돌파|문파|무공|내공|운기조식|캐릭터>");
        return 1;
    }

    private int openProfile(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        context.ui().openProfile(command.getSource().getPlayerOrException());
        return 1;
    }

    private int openSect(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        context.ui().openSect(command.getSource().getPlayerOrException());
        return 1;
    }

    private int openMartialArts(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        context.ui().openMartialArts(command.getSource().getPlayerOrException());
        return 1;
    }

    private int profile(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        return async(command, context.profiles().loadOrCreate(player.getUUID()), profile -> profileText(profile));
    }

    private int realm(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        return async(command, context.progressionService().realm(player.getUUID()), this::realmText);
    }

    private int cultivate(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        CommandSourceStack source = command.getSource();
        context.progressionService().promote(player.getUUID(), source.getServer().getTickCount()).whenComplete((result, error) ->
                source.getServer().executeIfPossible(() -> {
                    if (error != null) { source.sendFailure(Component.literal("[강호] " + root(error).getMessage())); return; }
                    if (result.status() == PromotionResult.Status.SUCCESS) context.visualEffects().play(player, "REALM_BREAKTHROUGH_SUCCESS", player);
                    else if (result.status() == PromotionResult.Status.BLOCKED) context.visualEffects().play(player, "REALM_BREAKTHROUGH_FAIL", player);
                    send(source, promotionText(result));
                }));
        return 1;
    }

    private int menu(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        if (context.menu().tryOpen(player.getUUID(), System.currentTimeMillis()) == com.murimrpg.service.InteractiveMenuService.OpenResult.COOLDOWN) {
            command.getSource().sendFailure(Component.literal("[강호 메뉴] 잠시 후 다시 열 수 있습니다."));
            return 0;
        }
        var settings = context.menu().settings();
        player.sendSystemMessage(Component.literal(settings.title()));
        sendMenuButton(player, "[내 정보]", "/강호 내정보", "현재 경지와 캐릭터 정보를 봅니다.");
        sendMenuButton(player, "[경지]", "/강호 경지", "다음 경지와 돌파 조건을 봅니다.");
        sendMenuButton(player, "[문파]", "/강호 문파", "문파 정보를 봅니다.");
        sendMenuButton(player, "[무공]", "/강호 무공", "현재 무공을 봅니다.");
        sendMenuButton(player, "[화폐]", "/화폐", "인벤토리 속 화폐를 확인합니다.");
        sendMenuButton(player, "[도움말]", "/강호 도움말", "사용 가능한 강호 명령어를 봅니다.");
        player.sendSystemMessage(Component.literal("[스폰] " + settings.spawnMessage()));
        player.sendSystemMessage(Component.literal("[수련장] " + settings.trainingMessage()));
        return 1;
    }

    private static void sendMenuButton(ServerPlayer player, String label, String command, String tooltip) {
        player.sendSystemMessage(Component.literal(label).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip)))));
    }

    private int sect(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = command.getSource().getPlayerOrException();
        return async(command, context.content().loadOrCreate(player.getUUID()), journey -> "[문파] " + journey.content().organizationId().orElse("무소속")
                + " · 계급 " + journey.content().organizationRankId().orElse("없음") + " · 공헌 " + journey.content().organizationContribution());
    }

    private int sectList(CommandContext<CommandSourceStack> command) {
        String names = context.catalog().factions().organizations().stream().map(value -> value.displayName()).reduce((a,b) -> a + ", " + b).orElse("없음");
        send(command.getSource(), "[문파 목록] " + names);
        return 1;
    }

    private int joinHuashan(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.huashan().apply(player.getUUID(), context.catalog().huashanSettings().organizationId()), result -> {
            scheduleTrialEntity(command.getSource(), result.state());
            return "[화산파] " + result.message();
        });
    }

    private int sectPromote(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.huashan().startPromotionTrial(player.getUUID()), result -> {
            scheduleTrialEntity(command.getSource(), result.state());
            return "[화산파] " + result.message();
        });
    }

    private int martialArts(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.content().loadOrCreate(player.getUUID()), journey -> "[무공] 습득: "
                + (journey.content().learnedSkillIds().isEmpty() ? "없음" : String.join(", ", journey.content().learnedSkillIds()))
                + " · 활성 심법: " + journey.journey().activeSkillId().orElse("없음"));
    }

    private int qi(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.content().loadOrCreate(player.getUUID()), journey -> "[내공] " + journey.journey().currentQi());
    }

    private int meditate(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        send(command.getSource(), "[운기조식] " + context.meditation().start(player));
        return 1;
    }

    private int character(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        context.ui().openCharacterCreation(player);
        return 1;
    }

    private int reroll(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.characters().reroll(player.getUUID()), this::characterText);
    }

    private int confirm(CommandContext<CommandSourceStack> command, Family family) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.characters().confirm(player.getUUID(), family), data -> "[운명 확정] " + data.family().displayName());
    }

    private int save(CommandContext<CommandSourceStack> command) {
        return async(command, context.profiles().saveCachedProfiles(), result -> "[관리] 프로필 " + result.successCount() + "개 저장, 실패 " + result.failureCount());
    }

    private int prepareTestProgress(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.progressionService().prepareTestProgress(player.getUUID()), profile ->
                "[관리] 경지 테스트 준비 완료 · " + profile.realmDisplayName() + " · EXP " + profile.cultivationExp()
                        + " · 전체 경계 조건 부여");
    }

    private int clearTestProgress(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.progressionService().clearTestProgress(player.getUUID()), profile ->
                "[관리] 경지 테스트 초기화 완료 · " + profile.realmDisplayName() + " · EXP " + profile.cultivationExp());
    }

    private int requirement(CommandContext<CommandSourceStack> command, String id, boolean grant)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        var operation = grant ? context.progressionService().grantRequirement(player.getUUID(), id)
                : context.progressionService().revokeRequirement(player.getUUID(), id);
        return async(command, operation, change -> "[관리] 경계 조건 " + change.requirementId()
                + (change.granted() ? " 부여" : " 제거") + (change.changed() ? " 완료" : " · 변경 없음"));
    }

    private int addExp(CommandContext<CommandSourceStack> command) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = command.getSource().getPlayerOrException();
        return async(command, context.progressionService().addExp(player.getUUID(), LongArgumentType.getLong(command, "amount")), result -> "[관리] EXP " + result.after());
    }

    private boolean isOperator(CommandSourceStack source) {
        return source.getPlayer() == null || source.getServer().getPlayerList().isOp(source.getPlayer().nameAndId());
    }

    private <T> int async(CommandContext<CommandSourceStack> command, CompletionStage<T> stage, Function<T, String> message) {
        CommandSourceStack source = command.getSource();
        stage.whenComplete((value, error) -> source.getServer().executeIfPossible(() -> {
            if (error == null) send(source, message.apply(value));
            else source.sendFailure(Component.literal("[강호] " + root(error).getMessage()));
        }));
        return 1;
    }

    private String profileText(PlayerProfile p) {
        return "[내정보] 경지 " + p.realmDisplayName() + " · 수련 EXP " + p.cultivationExp() + " · 가문 "
                + (p.characterData().completed() ? p.characterData().family().displayName() : "미정");
    }
    private String realmText(RealmProgressView v) {
        return "[경지] " + v.currentStage().displayName() + v.nextStage().map(next -> " → " + next.displayName()
                + " (부족 EXP " + v.missingExp() + ", 조건 " + v.missingRequirements() + ")").orElse(" · 최종 경지");
    }
    private String promotionText(PromotionResult r) {
        return switch (r.status()) {
            case SUCCESS -> "[돌파 성공] " + r.nextStage().orElseThrow().displayName() + " · 해금 " + r.newlyUnlocked();
            case BLOCKED -> "[돌파 실패] 부족 EXP " + r.missingExp() + " · 조건 " + r.missingRequirements();
            case MAXIMUM_STAGE -> "[경지] 이미 최종 경지입니다.";
            case IN_PROGRESS -> "[경지] 돌파 요청을 처리 중입니다.";
        };
    }
    private String characterText(CharacterData data) {
        if (data.completed()) return "[운명] " + data.family().displayName() + " · 근력 " + data.baseStrength() + " · 속도 " + data.baseSpeed();
        var d = data.draft();
        return "[운명 후보] 가문 " + d.offeredFamilies().stream().map(Family::displayName).toList() + " (길거리 출신 포함) · 근력 "
                + d.baseStrength() + " · 속도 " + d.baseSpeed() + " · 재능 x" + d.talentMultiplier() + " · 체질 " + d.constitution().displayName()
                + " · /강호 character confirm <family-id>";
    }
    private void scheduleTrialEntity(CommandSourceStack source, PlayerJourney journey) {
        journey.journey().activeActivityId().ifPresent(activity -> {
            String entity;
            String role;
            if (activity.equals(context.catalog().huashanSettings().entranceTrialId())
                    || activity.equals(context.catalog().huashanSettings().liuheQuestId())) {
                entity = "minecraft:armor_stand"; role = "training_dummy";
            } else if (activity.equals(context.catalog().huashanSettings().secondPromotionTrialId())) {
                entity = "minecraft:villager"; role = "examiner";
            } else if (activity.equals(context.catalog().huashanSettings().firstPromotionTrialId())) {
                entity = "minecraft:zombie"; role = "bandit_leader";
            } else return;
            long delay = context.catalog().huashanSettings().trialSpawnDelaySeconds() * 20L;
            context.scheduler().schedule(source.getServer(), delay, () -> {
                ServerPlayer player = source.getPlayer();
                if (player == null || player.hasDisconnected()) return;
                String tags = "{Tags:[\"murimrpg." + role + "\",\"murimrpg.owner." + player.getUUID() + "\"]}";
                source.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(),
                        "summon " + entity + " ~ ~ ~ " + tags);
                player.sendSystemMessage(Component.literal("[화산파] 시험 대상이 나타났습니다."));
            });
        });
    }
    private static void send(CommandSourceStack source, String message) { source.sendSystemMessage(Component.literal(message)); }
    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
}
