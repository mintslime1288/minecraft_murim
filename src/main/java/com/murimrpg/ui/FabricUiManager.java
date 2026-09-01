package com.murimrpg.ui;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.SkillDefinition;
import com.murimrpg.scheduler.ServerTickScheduler;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
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
    private final UiViewService views;
    private final ServerTickScheduler scheduler;
    private final QuestSidebar sidebar;

    public FabricUiManager(UiSettings settings, PlayerProfileService profiles, PlayerContentService content,
            ContentCatalog catalog, CultivationProgression progression, ServerTickScheduler scheduler) {
        this.settings = settings; this.profiles = profiles; this.content = content;
        this.views = new UiViewService(catalog, progression); this.scheduler = scheduler;
        this.sidebar = new QuestSidebar(content, views);
    }

    public void register() { ServerLifecycleEvents.SERVER_STARTED.register(this::scheduleTick); }

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
        player.openMenu(new SimpleMenuProvider((syncId, inventory, ignored) -> new GanghoMenu(syncId, inventory, box, click),
                Component.literal(title)));
    }
}
