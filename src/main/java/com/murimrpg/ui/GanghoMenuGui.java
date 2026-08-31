package com.murimrpg.ui;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationStageDefinition;
import com.murimrpg.domain.OrganizationDefinition;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.QuestDefinition;
import com.murimrpg.domain.RealmProgressView;
import com.murimrpg.domain.SkillDefinition;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Read-only navigation surface for the gangho systems.  It only renders snapshots supplied by
 * services; state-changing buttons delegate validation and persistence to the relevant service.
 */
public final class GanghoMenuGui implements Listener {
    private final ContentCatalog catalog;
    private final ProgressionService progression;
    private final HuashanService huashan;
    private final PlayerContentService content;
    private final PlayerProfileService profiles;
    private final GanghoProfileGui profileGui;
    private final Executor mainThread;

    public GanghoMenuGui(ContentCatalog catalog, ProgressionService progression, HuashanService huashan,
            PlayerContentService content, PlayerProfileService profiles, GanghoProfileGui profileGui, Executor mainThread) {
        this.catalog = catalog;
        this.progression = progression;
        this.huashan = huashan;
        this.content = content;
        this.profiles = profiles;
        this.profileGui = profileGui;
        this.mainThread = mainThread;
    }

    public void openMain(Player player, PlayerProfile profile, PlayerJourney journey) {
        Inventory inventory = inventory(Screen.MAIN, "江湖錄 · 강호 메뉴", 27);
        inventory.setItem(10, item(Material.PLAYER_HEAD, "인물 정보", NamedTextColor.GOLD,
                List.of("가문: " + profile.characterData().family().displayName(), "경지: " + profile.realmDisplayName(), "클릭하여 상세 정보를 확인합니다.")));
        inventory.setItem(11, item(Material.IRON_SWORD, "武功 · 무공", NamedTextColor.RED,
                List.of("습득: " + journey.content().learnedSkillIds().size() + "개", "활성 심법: " + activeSkill(journey), "클릭하여 무공을 확인합니다.")));
        inventory.setItem(12, item(Material.AMETHYST_SHARD, "境界 · 경지", NamedTextColor.AQUA,
                List.of("현재: " + profile.realmDisplayName(), "수련 EXP: " + profile.cultivationExp(), "클릭하여 돌파 조건을 확인합니다.")));
        inventory.setItem(14, item(Material.CHERRY_SAPLING, "門派 · 문파", NamedTextColor.LIGHT_PURPLE,
                List.of("소속: " + organizationName(journey), "계급: " + rankName(journey), "클릭하여 문파 정보를 확인합니다.")));
        inventory.setItem(15, item(Material.WRITABLE_BOOK, "任務 · 임무", NamedTextColor.YELLOW,
                List.of(activeQuest(journey).map(QuestDefinition::displayName).orElse("진행 중인 임무 없음"), "클릭하여 진행도를 확인합니다.")));
        inventory.setItem(16, item(Material.COMPASS, "江湖錄 · 도감", NamedTextColor.GREEN,
                List.of("강호 기록과 콘텐츠 목록", "추가 도감 콘텐츠는 준비 중입니다.")));
        inventory.setItem(22, item(Material.BARRIER, "닫기", NamedTextColor.RED, List.of("메뉴를 닫습니다.")));
        player.openInventory(inventory);
    }

    public void openRealm(Player player, RealmProgressView view) {
        Inventory inventory = inventory(Screen.REALM, "境界 · 경지", 27);
        CultivationStageDefinition current = view.currentStage();
        inventory.setItem(11, item(Material.AMETHYST_SHARD, "현재 경지", NamedTextColor.AQUA,
                List.of(current.displayName(), "수련 EXP: " + view.profile().cultivationExp(), "최대 내공: " + current.maxQi())));
        List<String> next = view.nextStage().map(stage -> List.of("다음: " + stage.displayName(),
                "필요 EXP: " + stage.requiredCultivationExp(), "부족 EXP: " + view.missingExp(),
                view.missingRequirements().isEmpty() ? "돌파 조건: 충족" : "미충족 조건: " + String.join(", ", view.missingRequirements())))
                .orElse(List.of("최고 경지에 도달했습니다."));
        inventory.setItem(13, item(Material.NETHER_STAR, "돌파 조건", NamedTextColor.LIGHT_PURPLE, next));
        inventory.setItem(15, item(view.nextStage().isPresent() ? Material.LIME_DYE : Material.GRAY_DYE,
                view.nextStage().isPresent() ? "돌파 시도" : "최고 경지", NamedTextColor.GOLD,
                view.nextStage().isPresent() ? List.of("클릭하면 현재 조건으로 돌파를 시도합니다.") : List.of("더 이상 돌파할 수 없습니다.")));
        back(inventory);
        player.openInventory(inventory);
    }

    public void openMartial(Player player, PlayerJourney journey) {
        Inventory inventory = inventory(Screen.MARTIAL, "武功 · 무공", 27);
        List<SkillDefinition> skills = catalog.skills();
        for (int index = 0; index < Math.min(18, skills.size()); index++) {
            SkillDefinition skill = skills.get(index);
            boolean learned = journey.content().learnedSkillIds().contains(skill.id());
            boolean active = journey.journey().activeSkillId().filter(skill.id()::equals).isPresent();
            List<String> lore = new java.util.ArrayList<>();
            lore.add("종류: " + skillType(skill)); lore.add("등급: " + skill.grade());
            lore.add("상태: " + (active ? "활성" : learned ? "습득 완료" : "미습득"));
            lore.addAll(skill.description());
            if (!learned) { lore.add("습득 조건:"); lore.addAll(skill.unlockConditions()); }
            if (learned && skill.type() == com.murimrpg.domain.SkillType.INTERNAL_ART) lore.add("클릭하여 활성 심법으로 설정합니다.");
            inventory.setItem(index, item(learned ? Material.ENCHANTED_BOOK : Material.BOOK, skill.displayName(),
                    learned ? NamedTextColor.GREEN : NamedTextColor.GRAY, lore));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    public void openSect(Player player, PlayerJourney journey) {
        Inventory inventory = inventory(Screen.SECT, "門派 · 문파", 27);
        Optional<OrganizationDefinition> organization = journey.content().organizationId().flatMap(catalog.factions()::findOrganization);
        if (organization.isEmpty()) {
            inventory.setItem(13, item(Material.OAK_SIGN, "현재 소속된 문파가 없습니다.", NamedTextColor.GRAY,
                    List.of("문파 목록과 입문은 /문파 명령에서 이용할 수 있습니다.")));
        } else {
            OrganizationDefinition value = organization.orElseThrow();
            inventory.setItem(10, item(Material.CHERRY_SAPLING, value.hanjaName().orElse("門派") + " · " + value.displayName(), NamedTextColor.LIGHT_PURPLE,
                    value.description()));
            inventory.setItem(12, item(Material.NAME_TAG, "현재 신분", NamedTextColor.GOLD,
                    List.of("계급: " + rankName(journey), "기여도: " + journey.content().organizationContribution())));
            inventory.setItem(14, item(Material.IRON_SWORD, "주요 계통", NamedTextColor.RED,
                    value.specialties()));
            inventory.setItem(16, item(Material.WRITABLE_BOOK, "문파 임무", NamedTextColor.YELLOW,
                    List.of(activeQuest(journey).map(QuestDefinition::displayName).orElse("진행 중인 문파 임무 없음"), "임무 화면에서 상세 진행도를 확인합니다.")));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    public void openQuest(Player player, PlayerJourney journey) {
        Inventory inventory = inventory(Screen.QUEST, "任務 · 임무", 27);
        Optional<QuestDefinition> active = activeQuest(journey);
        if (active.isEmpty()) {
            inventory.setItem(13, item(Material.PAPER, "진행 중인 임무가 없습니다.", NamedTextColor.GRAY,
                    List.of("문파 입문 또는 승급 시험을 시작하면", "이곳과 Sidebar에 진행도가 표시됩니다.")));
        } else {
            QuestDefinition quest = active.orElseThrow();
            List<String> lore = new java.util.ArrayList<>();
            lore.add("목표: " + objective(quest));
            if (quest.requiredHits() > 0) lore.add("기초 검술: " + journey.journey().dummyHits() + " / " + quest.requiredHits());
            if (quest.requiredMeditationSeconds() > 0) lore.add("운기조식: " + journey.journey().meditationSeconds() + " / " + quest.requiredMeditationSeconds() + "초");
            quest.grantedSkillId().flatMap(catalog::findSkill).ifPresent(skill -> lore.add("보상 무공: " + skill.displayName()));
            inventory.setItem(13, item(Material.WRITABLE_BOOK, quest.displayName(), NamedTextColor.YELLOW, lore));
        }
        back(inventory);
        player.openInventory(inventory);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlot() == 22) { player.closeInventory(); return; }
        UUID uuid = player.getUniqueId();
        switch (holder.screen()) {
            case MAIN -> openMainClick(player, event.getRawSlot());
            case REALM -> { if (event.getRawSlot() == 15) promote(player, uuid); else if (event.getRawSlot() == 18) openMainFromCache(player); }
            case MARTIAL -> { if (event.getRawSlot() < 18 && event.getRawSlot() < catalog.skills().size()) equip(player, uuid, catalog.skills().get(event.getRawSlot()).id()); else if (event.getRawSlot() == 18) openMainFromCache(player); }
            case SECT, QUEST -> { if (event.getRawSlot() == 18) openMainFromCache(player); }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void openMainClick(Player player, int slot) {
        switch (slot) {
            case 10 -> openProfile(player);
            case 11 -> cachedJourney(player).ifPresentOrElse(value -> openMartial(player, value), () -> loading(player));
            case 12 -> progression.realm(player.getUniqueId()).whenCompleteAsync((view, error) -> {
                if (error != null) failed(player); else openRealm(player, view);
            }, mainThread);
            case 14 -> cachedJourney(player).ifPresentOrElse(value -> openSect(player, value), () -> loading(player));
            case 15 -> cachedJourney(player).ifPresentOrElse(value -> openQuest(player, value), () -> loading(player));
            case 16 -> player.sendMessage(Component.text("[강호이문록] 도감 확장 콘텐츠는 준비 중입니다.", NamedTextColor.GRAY));
            default -> { }
        }
    }

    private void promote(Player player, UUID uuid) {
        progression.promote(uuid).whenCompleteAsync((result, error) -> {
            if (error != null) { failed(player); return; }
            switch (result.status()) {
                case SUCCESS -> player.sendMessage(Component.text("[강호이문록] 돌파 성공: " + result.profile().orElseThrow().realmDisplayName(), NamedTextColor.GREEN));
                case BLOCKED -> player.sendMessage(Component.text("[강호이문록] 돌파 조건이 부족합니다. 부족 EXP: " + result.missingExp(), NamedTextColor.RED));
                case MAXIMUM_STAGE -> player.sendMessage(Component.text("[강호이문록] 현재 최고 경지입니다.", NamedTextColor.YELLOW));
                case IN_PROGRESS -> player.sendMessage(Component.text("[강호이문록] 이미 돌파를 처리 중입니다.", NamedTextColor.YELLOW));
            }
            progression.realm(uuid).whenCompleteAsync((view, refreshError) -> { if (refreshError == null) openRealm(player, view); }, mainThread);
        }, mainThread);
    }

    private void equip(Player player, UUID uuid, String skillId) {
        SkillDefinition skill = catalog.findSkill(skillId).orElseThrow();
        if (skill.type() != com.murimrpg.domain.SkillType.INTERNAL_ART) {
            player.sendMessage(Component.text("[강호이문록] 이 무공은 아직 장착 슬롯을 지원하지 않습니다.", NamedTextColor.GRAY)); return;
        }
        huashan.equip(uuid, skillId).whenCompleteAsync((result, error) -> {
            if (error != null) { player.sendMessage(Component.text("[강호이문록] 습득한 내공심법만 활성화할 수 있습니다.", NamedTextColor.RED)); return; }
            player.sendMessage(Component.text("[강호이문록] " + skill.displayName() + "을 활성 심법으로 설정했습니다.", NamedTextColor.GREEN));
            openMartial(player, result.state());
        }, mainThread);
    }

    private Optional<PlayerJourney> cachedJourney(Player player) { return content.cached(player.getUniqueId()); }
    private void openProfile(Player player) {
        Optional<PlayerProfile> profile = profiles.cachedProfile(player.getUniqueId());
        Optional<PlayerJourney> journey = cachedJourney(player);
        if (profile.isEmpty() || journey.isEmpty()) { loading(player); return; }
        profileGui.open(player, profile.orElseThrow(), journey.orElseThrow(), journey.orElseThrow().content().organizationId().flatMap(catalog.factions()::findOrganization));
    }
    private void openMainFromCache(Player player) {
        Optional<PlayerProfile> profile = profiles.cachedProfile(player.getUniqueId());
        Optional<PlayerJourney> journey = cachedJourney(player);
        if (profile.isEmpty() || journey.isEmpty()) { loading(player); return; }
        openMain(player, profile.orElseThrow(), journey.orElseThrow());
    }
    private void loading(Player player) { player.sendMessage(Component.text("[강호이문록] 플레이어 정보를 불러오는 중입니다. 잠시 후 다시 시도하세요.", NamedTextColor.YELLOW)); }
    private void failed(Player player) { player.sendMessage(Component.text("[강호이문록] 정보를 처리하지 못했습니다. 잠시 후 다시 시도하세요.", NamedTextColor.RED)); }
    private Optional<QuestDefinition> activeQuest(PlayerJourney journey) { return journey.journey().activeActivityId().flatMap(catalog.quests()::find); }
    private String activeSkill(PlayerJourney journey) { return journey.journey().activeSkillId().flatMap(catalog::findSkill).map(SkillDefinition::displayName).orElse("없음"); }
    private String organizationName(PlayerJourney journey) { return journey.content().organizationId().flatMap(catalog.factions()::findOrganization).map(OrganizationDefinition::displayName).orElse("없음"); }
    private String rankName(PlayerJourney journey) { return journey.content().organizationRankId().orElse("없음"); }
    private static String skillType(SkillDefinition skill) { return switch (skill.type()) { case INTERNAL_ART -> "내공심법"; case MARTIAL_ART -> "무공"; case MOVEMENT -> "경공"; }; }
    private static String objective(QuestDefinition quest) { return switch (quest.objectiveType()) { case TRAINING -> "수련"; case NPC_HEALTH_THRESHOLD -> "시험관 대련"; case MONSTER_KILL -> "대상 처치"; }; }
    private static Inventory inventory(Screen screen, String title, int size) { Holder holder = new Holder(screen); Inventory inventory = Bukkit.createInventory(holder, size, Component.text(title, NamedTextColor.DARK_PURPLE)); holder.bind(inventory); return inventory; }
    private static void back(Inventory inventory) { inventory.setItem(18, item(Material.ARROW, "뒤로", NamedTextColor.YELLOW, List.of("강호 메뉴로 돌아갑니다."))); inventory.setItem(22, item(Material.BARRIER, "닫기", NamedTextColor.RED, List.of("메뉴를 닫습니다."))); }
    private static ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false)); meta.lore(lore.stream().map(value -> Component.text(value, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)).toList()); stack.setItemMeta(meta); return stack; }
    private enum Screen { MAIN, REALM, MARTIAL, SECT, QUEST }
    private static final class Holder implements InventoryHolder { private final Screen screen; private Inventory inventory; Holder(Screen screen) { this.screen = screen; } void bind(Inventory inventory) { this.inventory = inventory; } Screen screen() { return screen; } @Override public @NotNull Inventory getInventory() { if (inventory == null) throw new IllegalStateException("Gangho menu is not initialized"); return inventory; } }
}
