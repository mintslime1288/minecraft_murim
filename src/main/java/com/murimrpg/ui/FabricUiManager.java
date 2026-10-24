package com.murimrpg.ui;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.CharacterData;
import com.murimrpg.domain.CharacterDraft;
import com.murimrpg.domain.CharacterTrait;
import com.murimrpg.domain.Family;
import com.murimrpg.domain.SkillDefinition;
import com.murimrpg.scheduler.ServerTickScheduler;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.CharacterCreationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Items;

/** Server-only Vanilla UI adapter. All displayed state comes from memory caches. */
public final class FabricUiManager {
    private static final int SECT_SLOT = 10, REALM_SLOT = 12, QI_SLOT = 14, SKILLS_SLOT = 16, BACK_SLOT = 22;
    private final UiSettings settings;
    private final PlayerProfileService profiles;
    private final PlayerContentService content;
    private final CharacterCreationService characters;
    private final UiViewService views;
    private final ServerTickScheduler scheduler;
    private final QuestSidebar sidebar;

    public FabricUiManager(UiSettings settings, PlayerProfileService profiles, PlayerContentService content,
            CharacterCreationService characters, ContentCatalog catalog, CultivationProgression progression,
            ServerTickScheduler scheduler) {
        this.settings = settings; this.profiles = profiles; this.content = content;
        this.characters = characters;
        this.views = new UiViewService(catalog, progression); this.scheduler = scheduler;
        this.sidebar = new QuestSidebar(content, views);
    }

    public void register() { ServerLifecycleEvents.SERVER_STARTED.register(this::scheduleTick); }

    public void openCharacterCreation(ServerPlayer player) {
        characters.ensureDraft(player.getUUID()).whenComplete((data, error) -> player.level().getServer().executeIfPossible(() -> {
            if (error != null) { failure(player, "운명 후보를 불러오지 못했습니다.", error); return; }
            openCharacterCreation(player, data);
        }));
    }

    public void openCharacterCreation(ServerPlayer player, CharacterData data) {
        if (data.completed()) { player.sendSystemMessage(Component.literal("[강호] 이미 운명을 확정했습니다.")); return; }
        CharacterDraft draft = data.draft();
        if (draft == null) { openCharacterCreation(player); return; }
        SimpleContainer box = new SimpleContainer(45);
        box.setItem(4, GuiItems.button(Items.NETHER_STAR, "당신의 운명이 결정되었습니다.", "가문을 선택한 뒤 확정하세요."));
        List<Family> families = availableFamilies(draft);
        for (int index = 0; index < families.size(); index++) {
            Family family = families.get(index);
            box.setItem(10 + index, GuiItems.button(family == draft.selectedFamily() ? Items.EMERALD : Items.PAPER,
                    family == Family.STREET ? "길거리 출신" : family.displayName(),
                    family == draft.selectedFamily() ? "선택됨" : "클릭하여 선택"));
        }
        Family selected = draft.selectedFamily();
        int strengthBonus = selected == Family.STREET ? 0 : characters.settings().strengthBonuses().getOrDefault(selected, 0);
        int speedBonus = selected == Family.STREET ? 0 : characters.settings().speedBonuses().getOrDefault(selected, 0);
        box.setItem(28, GuiItems.button(Items.IRON_INGOT, "힘: " + draft.baseStrength() + signed(strengthBonus)
                + " → " + (draft.baseStrength() + strengthBonus), "기본값 / 가문 보정 / 최종값"));
        box.setItem(29, GuiItems.button(Items.SUGAR, "속도: " + draft.baseSpeed() + signed(speedBonus)
                + " → " + (draft.baseSpeed() + speedBonus), "기본값 / 가문 보정 / 최종값"));
        box.setItem(30, GuiItems.button(Items.EXPERIENCE_BOTTLE, "재능: " + (int) (draft.talentMultiplier() * 100) + "%", "숙련도 획득 배율"));
        box.setItem(31, GuiItems.button(Items.HEART_OF_THE_SEA, "체질: " + draft.constitution().displayName(), draft.constitution().description()));
        CharacterTrait[] traits = CharacterTrait.values();
        for (int index = 0; index < traits.length; index++) {
            boolean locked = draft.lockedTraits().contains(traits[index]);
            box.setItem(33 + index, GuiItems.button(locked ? Items.TRIPWIRE_HOOK : Items.LEVER,
                    traitName(traits[index]) + (locked ? " 잠금" : " 잠금 해제"),
                    "클릭: 리롤 시 " + (locked ? "유지" : "다시 결정")));
        }
        box.setItem(40, GuiItems.button(Items.EMERALD_BLOCK, "확정", "이 선택은 영구 저장됩니다."));
        int remaining = characters.settings().maxRerolls() - data.rerollsUsed();
        box.setItem(42, GuiItems.button(remaining <= 0 ? Items.BARRIER : Items.REDSTONE,
                "다시 뽑기", "남은 리롤: " + remaining + "회"));
        open(player, "강호이문록 | 운명 결정", box, 5, slot -> handleCharacterClick(player, slot));
    }

    public void openProfile(ServerPlayer player) {
        snapshot(player).ifPresentOrElse(view -> {
            SimpleContainer box = new SimpleContainer(27);
            box.setItem(4, GuiItems.button(Items.PLAYER_HEAD, player.getGameProfile().name(),
                    "문파: " + view.organization().map(value -> value.displayName()).orElse("없음"),
                    "신분: " + view.rankDisplayName(), "경지: " + view.profile().realmDisplayName()));
            box.setItem(SECT_SLOT, GuiItems.button(Items.IRON_SWORD, "門派 · 문파",
                    view.organization().map(value -> value.displayName()).orElse("소속 없음"), "현재 신분: " + view.rankDisplayName()));
            box.setItem(REALM_SLOT, GuiItems.button(Items.AMETHYST_SHARD, "境界 · 경지",
                    view.profile().realmDisplayName(), "수련 EXP: " + view.profile().cultivationExp()));
            box.setItem(QI_SLOT, GuiItems.button(Items.EXPERIENCE_BOTTLE, "內功 · 내공", view.currentQi() + " / " + view.maxQi()));
            box.setItem(SKILLS_SLOT, GuiItems.button(Items.ENCHANTED_BOOK, "武功 · 보유 무공",
                    view.playerJourney().content().learnedSkillIds().size() + "개", "클릭하여 확인합니다."));
            box.setItem(BACK_SLOT, GuiItems.button(Items.BARRIER, "닫기", "메뉴를 닫습니다."));
            open(player, "江湖錄 · 강호록", box, slot -> {
                if (slot == SECT_SLOT) openSect(player); else if (slot == SKILLS_SLOT) openMartialArts(player);
                else if (slot == BACK_SLOT) player.closeContainer();
            });
        }, () -> player.sendOverlayMessage(Component.literal("플레이어 데이터를 불러오는 중입니다.")));
    }

    public void openSect(ServerPlayer player) {
        snapshot(player).ifPresentOrElse(view -> {
            if (view.organization().isEmpty()) { player.sendOverlayMessage(Component.literal("현재 소속된 문파가 없습니다.")); return; }
            var organization = view.organization().orElseThrow();
            SimpleContainer box = new SimpleContainer(27);
            List<String> summary = new ArrayList<>();
            summary.add("현재 신분: " + view.rankDisplayName()); summary.addAll(organization.specialties()); summary.addAll(organization.description());
            box.setItem(4, GuiItems.item(Items.CHERRY_SAPLING, organization.displayName(), summary));
            box.setItem(10, GuiItems.button(Items.WRITABLE_BOOK, "문파 정보", "문파의 내력과 특징을 확인합니다."));
            box.setItem(SKILLS_SLOT, GuiItems.button(Items.ENCHANTED_BOOK, "문파 무공", "습득 및 해금 상태를 확인합니다."));
            box.setItem(14, GuiItems.button(Items.NETHER_STAR, "승급 시험", "명령어 /강호 문파 승급"));
            box.setItem(16, GuiItems.button(Items.MAP, "문파 임무", view.playerJourney().journey().activeActivityId().orElse("진행 중인 임무 없음")));
            box.setItem(BACK_SLOT, GuiItems.button(Items.ARROW, "뒤로", "강호록으로 돌아갑니다."));
            open(player, organization.hanjaName().map(value -> value + " · ").orElse("") + organization.displayName(), box,
                    slot -> { if (slot == SKILLS_SLOT) openMartialArts(player); else if (slot == BACK_SLOT) openProfile(player); });
        }, () -> player.sendOverlayMessage(Component.literal("플레이어 데이터를 불러오는 중입니다.")));
    }

    public void openMartialArts(ServerPlayer player) {
        snapshot(player).ifPresentOrElse(view -> {
            if (view.organization().isEmpty()) { player.sendOverlayMessage(Component.literal("현재 소속된 문파가 없습니다.")); return; }
            SimpleContainer box = new SimpleContainer(27);
            List<SkillDefinition> skills = views.organizationSkills(view.organization().orElseThrow().id());
            int[] slots = {11, 13, 15};
            for (int i = 0; i < Math.min(slots.length, skills.size()); i++) {
                SkillDefinition skill = skills.get(i);
                boolean learned = view.playerJourney().content().learnedSkillIds().contains(skill.id());
                List<String> lore = new ArrayList<>();
                lore.add("종류: " + skill.type()); lore.add("등급: " + skill.grade()); lore.add("상태: " + (learned ? "습득 완료" : "잠김"));
                if (!learned) lore.addAll(skill.unlockConditions()); lore.addAll(skill.description());
                box.setItem(slots[i], GuiItems.item(learned ? Items.ENCHANTED_BOOK : Items.BOOK, skill.displayName(), lore));
            }
            box.setItem(BACK_SLOT, GuiItems.button(Items.ARROW, "뒤로", "문파 화면으로 돌아갑니다."));
            open(player, "武功 · " + view.organization().orElseThrow().displayName(), box,
                    slot -> { if (slot == BACK_SLOT) openSect(player); });
        }, () -> player.sendOverlayMessage(Component.literal("플레이어 데이터를 불러오는 중입니다.")));
    }

    public void clear(ServerPlayer player) { sidebar.clear(player); }

    private void scheduleTick(MinecraftServer server) {
        scheduler.schedule(server, settings.refreshTicks(), () -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
            scheduleTick(server);
        });
    }

    private void tick(ServerPlayer player) {
        snapshot(player).ifPresent(view -> {
            String skill = view.selectedSkill().map(value -> value.displayName()).orElse("선택 무공 없음");
            String status = view.martialArtStatus().displayName();
            player.sendOverlayMessage(Component.literal("☯ 내공 " + view.currentQi() + "/" + view.maxQi() + " | ⚔ " + skill + " | " + status));
        });
        sidebar.tick(player);
    }

    private Optional<PlayerUiSnapshot> snapshot(ServerPlayer player) {
        return profiles.cachedProfile(player.getUUID()).flatMap(profile ->
                content.cached(player.getUUID()).map(journey -> views.snapshot(profile, journey, 0)));
    }

    private void open(ServerPlayer player, String title, SimpleContainer box, java.util.function.IntConsumer click) {
        open(player, title, box, 3, click);
    }

    private void open(ServerPlayer player, String title, SimpleContainer box, int rows, java.util.function.IntConsumer click) {
        player.openMenu(new SimpleMenuProvider((syncId, inventory, ignored) -> new GanghoMenu(syncId, inventory, box, rows, click),
                Component.literal(title)));
    }

    private void handleCharacterClick(ServerPlayer player, int slot) {
        characters.ensureDraft(player.getUUID()).whenComplete((data, loadError) -> player.level().getServer().executeIfPossible(() -> {
            if (loadError != null) { failure(player, "운명 정보를 불러오지 못했습니다.", loadError); return; }
            if (data.completed() || data.draft() == null) return;
            CharacterDraft draft = data.draft();
            List<Family> families = availableFamilies(draft);
            int familyIndex = slot - 10;
            if (familyIndex >= 0 && familyIndex < families.size()) {
                refresh(player, characters.selectFamily(player.getUUID(), families.get(familyIndex)), "가문 선택에 실패했습니다.");
            } else if (slot >= 33 && slot < 33 + CharacterTrait.values().length) {
                refresh(player, characters.toggleLock(player.getUUID(), CharacterTrait.values()[slot - 33]), "특성 잠금 변경에 실패했습니다.");
            } else if (slot == 42) {
                refresh(player, characters.reroll(player.getUUID()), "리롤할 수 없습니다.");
            } else if (slot == 40) {
                characters.confirm(player.getUUID(), draft.selectedFamily()).whenComplete((confirmed, error) ->
                        player.level().getServer().executeIfPossible(() -> {
                            if (error != null) { failure(player, "운명 확정에 실패했습니다.", error); return; }
                            player.closeContainer();
                            player.sendSystemMessage(Component.literal("[강호] 캐릭터 생성 완료: " + confirmed.family().displayName()));
                            openProfile(player);
                        }));
            }
        }));
    }

    private void refresh(ServerPlayer player, java.util.concurrent.CompletionStage<CharacterData> operation, String message) {
        operation.whenComplete((updated, error) -> player.level().getServer().executeIfPossible(() -> {
            if (error != null) { failure(player, message, error); return; }
            openCharacterCreation(player, updated);
        }));
    }

    private static List<Family> availableFamilies(CharacterDraft draft) {
        return java.util.Arrays.stream(Family.values())
                .filter(family -> family == Family.STREET || draft.offeredFamilies().contains(family)).toList();
    }

    private static String signed(int value) { return " (" + (value >= 0 ? "+" : "") + value + ")"; }
    private static String traitName(CharacterTrait trait) {
        return switch (trait) {
            case FAMILY -> "가문";
            case TALENT -> "재능";
            case STRENGTH -> "힘";
            case SPEED -> "속도";
            case CONSTITUTION -> "체질";
        };
    }
    private static void failure(ServerPlayer player, String message, Throwable error) {
        player.sendSystemMessage(Component.literal("[강호] " + message + " " + root(error).getMessage()));
    }
    private static Throwable root(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error; }
}
