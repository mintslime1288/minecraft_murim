package com.murimrpg.command;

import com.murimrpg.domain.GanghoCommandCatalog;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.OrganizationDefinition;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.GanghoAdministrationService;
import com.murimrpg.infrastructure.paper.HuashanExamEntities;
import com.murimrpg.infrastructure.paper.HuashanTrialSpawner;
import com.murimrpg.ui.CharacterCreationGui;
import com.murimrpg.ui.GanghoUiManager;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Korean command adapter. It delegates persistent character work to existing services. */
public final class GanghoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT = List.of("도움말", "내정보", "상태", "설정", "공지");
    private static final List<String> SECT = List.of("정보", "목록", "가입", "탈퇴", "위치", "내정보", "계급", "승급", "공헌도", "임무", "상점", "무공");
    private static final List<String> MARTIAL = List.of("목록", "정보", "장착", "해제", "숙련도", "심법", "검법", "도법", "권법", "장법", "지법", "경공", "절기");
    private final GanghoCommandCatalog catalog; private final PlayerProfileService profiles;
    private final ProgressionService progression; private final CharacterCreationService characters;
    private final CharacterCreationGui characterGui; private final Executor mainThread;
    private final GanghoUiManager uiManager;
    private final ContentCatalog contentCatalog; private final HuashanService huashan;
    private final PlayerContentService contentStates; private final HuashanExamEntities examEntities;
    private final GanghoAdministrationService administration;
    private final HuashanTrialSpawner trialSpawner;
    public GanghoCommand(GanghoCommandCatalog catalog, PlayerProfileService profiles, ProgressionService progression,
            CharacterCreationService characters, CharacterCreationGui characterGui, Executor mainThread,
            GanghoUiManager uiManager, ContentCatalog contentCatalog, HuashanService huashan,
            PlayerContentService contentStates, HuashanExamEntities examEntities,
            GanghoAdministrationService administration, HuashanTrialSpawner trialSpawner) {
        this.catalog = catalog; this.profiles = profiles; this.progression = progression; this.characters = characters; this.characterGui = characterGui; this.mainThread = mainThread; this.uiManager = uiManager;
        this.contentCatalog = contentCatalog; this.huashan = huashan; this.contentStates = contentStates; this.examEntities = examEntities;
        this.administration = administration;
        this.trialSpawner = trialSpawner;
    }
    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String name = command.getName();
        if (name.equals("gangho_admin")) return admin(sender, args);
        if (name.equals("gangho_dev")) return dev(sender, args);
        Player player = player(sender); if (player == null) return true;
        if (!player.hasPermission("gangho.command")) { error(player, "권한이 없습니다: gangho.command"); return true; }
        return switch (name) {
            case "gangho" -> root(player, args); case "gangho_profile" -> profile(player); case "gangho_sect" -> sect(player, args);
            case "gangho_martial" -> martial(player, args); case "gangho_qi" -> qi(player, args); case "gangho_meditate" -> ready(player, "운기조식");
            case "gangho_realm" -> realm(player, args); case "gangho_quest" -> ready(player, "퀘스트");
            case "gangho_encounter" -> ready(player, "기연"); case "gangho_codex" -> ready(player, "도감");
            case "gangho_map", "gangho_region", "gangho_route" -> ready(player, "지도/길찾기"); default -> true; };
    }
    private boolean root(Player player, String[] args) { if (args.length == 0 || args[0].equals("메뉴")) { menu(player); return true; } switch (args[0]) { case "도움말" -> help(player); case "내정보", "상태" -> profile(player); case "설정" -> characterGui.openIfNeeded(player); case "공지" -> info(player, "현재 공지가 없습니다."); default -> error(player, "사용법: /강호 <메뉴|도움말|내정보|상태|설정|공지>"); } return true; }
    private void menu(Player player) { profiles.loadOrCreate(player.getUniqueId()).whenCompleteAsync((profile, error) -> {
        if (error != null) { error(player, "인물정보를 불러오지 못했습니다."); return; }
        contentStates.loadOrCreate(player.getUniqueId()).whenCompleteAsync((journey, contentError) -> {
            if (contentError != null) { error(player, "강호 정보를 불러오지 못했습니다."); return; }
            if (!uiManager.openMenu(player, profile, journey)) { info(player, "먼저 운명 결정을 완료하세요."); characterGui.openIfNeeded(player); }
        }, mainThread);
    }, mainThread); }
    private boolean profile(Player player) { profiles.loadOrCreate(player.getUniqueId()).whenCompleteAsync((profile, error) -> {
        if (error != null) { error(player, "인물정보를 불러오지 못했습니다."); return; }
        contentStates.loadOrCreate(player.getUniqueId()).whenCompleteAsync((journey, contentError) -> {
            if (contentError != null) { error(player, "문파 정보를 불러오지 못했습니다."); return; }
            if (!uiManager.openProfile(player, profile, journey)) {
                info(player, "먼저 운명 결정을 완료하세요.");
                characterGui.openIfNeeded(player);
            }
        }, mainThread);
    }, mainThread); return true; }
    private void showProfile(CommandSender sender, PlayerProfile profile) { var character = profile.characterData(); info(sender, "━━━ 강호이문록 · 인물정보 ━━━"); info(sender, "경지: " + profile.realmDisplayName() + " | 수련: " + profile.cultivationExp()); info(sender, "가문: " + (character.completed() ? character.family().displayName() : "미결정") + " | 체질: " + (character.completed() ? character.constitution().displayName() : "미결정")); if (character.completed()) info(sender, "기초 힘/신법: " + character.baseStrength() + "/" + character.baseSpeed() + " | 재능: x" + character.talentMultiplier()); info(sender, "문파·무공·내공: 미구현"); }
    private boolean sect(Player player, String[] args) {
        if (args.length == 0 || args[0].equals("목록")) { info(player, "공개 문파: " + visibleOrganizations().stream().map(OrganizationDefinition::displayName).collect(java.util.stream.Collectors.joining(", "))); return true; }
        if (args[0].equals("정보") && args.length == 2) { OrganizationDefinition value=findVisible(args[1]); if(value==null) error(player,"존재하지 않거나 공개되지 않은 문파입니다."); else info(player,value.displayName()+" ("+value.id()+"): "+String.join(" ",value.description())); return true; }
        if (args[0].equals("가입") && args.length == 2) { OrganizationDefinition value=findVisible(args[1]); if(value==null){error(player,"존재하지 않거나 공개되지 않은 문파입니다.");return true;} if(!value.id().equals("HUASHAN_SECT")){info(player,value.displayName()+" 입문 콘텐츠는 준비 중입니다.");return true;} huashan.apply(player.getUniqueId(),value.id()).whenCompleteAsync((r,e)->trialStarted(player,r,e),mainThread); return true; }
        if (args[0].equals("승급")) { huashan.startPromotionTrial(player.getUniqueId()).whenCompleteAsync((r,e)->trialStarted(player,r,e),mainThread); return true; }
        if (args[0].equals("내정보") || args[0].equals("계급")) { contentStates.loadOrCreate(player.getUniqueId()).whenCompleteAsync((s,e)->{if(e!=null)error(player,root(e).getMessage());else info(player,"소속="+s.content().organizationId().orElse("없음")+", 계급="+s.content().organizationRankId().orElse("없음")+", 활동="+s.journey().activeActivityId().orElse("없음"));},mainThread); return true; }
        if (SECT.contains(args[0])) ready(player,"문파 "+args[0]); else error(player,"사용법: /문파 <정보|목록|가입|내정보|계급|승급>"); return true;
    }
    private boolean martial(Player player, String[] args) { if (args.length == 0) { ready(player, "무공"); return true; } if (args[0].equals("목록") || MARTIAL.contains(args[0])) info(player, "등록 무공: " + catalog.martialArts().stream().map(GanghoCommandCatalog.Entry::displayName).collect(java.util.stream.Collectors.joining(", "))); else if (args[0].equals("정보") && args.length == 2) catalog.martialArt(args[1]).ifPresentOrElse(entry -> info(player, entry.displayName() + ": " + entry.description()), () -> error(player, "존재하지 않는 무공입니다.")); else if (args[0].equals("장착") || args[0].equals("해제") || args[0].equals("숙련도")) ready(player, "무공 " + args[0]); else error(player, "사용법: /무공 <목록|정보|장착|해제|숙련도|심법|검법|도법|권법|장법|지법|경공|절기>"); return true; }
    private boolean qi(Player player, String[] args) { if (args.length == 0 || args[0].equals("정보") || args[0].equals("심법")) ready(player, "내공"); else if (args[0].equals("운기조식")) ready(player, "운기조식"); else error(player, "사용법: /내공 <정보|심법|운기조식>"); return true; }
    private boolean realm(Player player, String[] args) { if (args.length > 0 && args[0].equals("돌파")) { progression.promote(player.getUniqueId()).whenCompleteAsync((result, error) -> { if(error != null) error(player,"돌파 처리에 실패했습니다."); else info(player, result.status().name()); }, mainThread); } else profile(player); return true; }
    private boolean admin(CommandSender sender, String[] args) { if (!sender.hasPermission("gangho.admin")) { error(sender,"권한이 없습니다: gangho.admin"); return true; } if (args.length == 3 && args[0].equals("유저") && args[1].equals("정보")) { Player target = Bukkit.getPlayerExact(args[2]); if(target == null) error(sender,"온라인 플레이어를 찾을 수 없습니다."); else profiles.loadOrCreate(target.getUniqueId()).whenCompleteAsync((profile,error)-> { if(error != null) error(sender,"조회 실패"); else showProfile(sender,profile); },mainThread); } else if(args.length==3 && args[0].equals("화산파")){Player target=Bukkit.getPlayerExact(args[2]);if(target==null){error(sender,"온라인 플레이어를 찾을 수 없습니다.");return true;}if(args[1].equals("시험관생성")){examEntities.spawnExaminer(target);info(sender,"시험관 생성 완료");}else if(args[1].equals("산적대장생성")){examEntities.spawnBanditLeader(target);info(sender,"산적 대장 생성 완료");}else error(sender,"알 수 없는 화산파 관리자 명령입니다.");} else info(sender,"사용법: /강호관리 <유저 정보|화산파 시험관생성|화산파 산적대장생성> <플레이어>"); return true; }
    private boolean dev(CommandSender sender, String[] args) { if (!sender.hasPermission("gangho.dev")) { error(sender,"권한이 없습니다: gangho.dev"); return true; } Player player=player(sender); if(player == null) return true; if(args.length == 1 && args[0].equals("reset")) administration.reset(player.getUniqueId()).whenCompleteAsync((result,error)-> { if(error != null) error(player,"초기화 실패"); else { info(player,"플레이어 정보와 문파·퀘스트 진행을 초기화했습니다."); characterGui.openIfNeeded(player); } },mainThread); else if(args.length == 1 && (args[0].equals("data") || args[0].equals("stat"))) profile(player); else info(player,"사용법: /강호dev <data|stat|reset|sect|martial|quest|effect>"); return true; }
    private void help(CommandSender sender) { info(sender,"━━━ 강호이문록 ━━━"); for(String line:List.of("[메뉴] /강호 또는 /ui","[캐릭터] /내정보 /경지","[문파] /문파","[무공] /무공 /내공","[모험] /퀘스트 /기연 /도감 /지도","[도움말] /강호 도움말")) info(sender,line); }
    private boolean ready(Player player,String feature){ info(player,feature+" 시스템은 현재 준비 중입니다."); return true; }
    private Player player(CommandSender sender){ if(sender instanceof Player player)return player; error(sender,"플레이어만 사용할 수 있는 명령어입니다."); return null; }
    private void info(CommandSender sender,String text){sender.sendMessage(Component.text("[강호이문록] "+text,NamedTextColor.YELLOW));} private void error(CommandSender sender,String text){sender.sendMessage(Component.text("[강호이문록] "+text,NamedTextColor.RED));}
    private List<OrganizationDefinition> visibleOrganizations(){return contentCatalog.factions().organizations().stream().filter(v->v.category()==com.murimrpg.domain.OrganizationCategory.NINE_SECTS_ONE_GANG).toList();}
    private OrganizationDefinition findVisible(String input){String value=input.toUpperCase(Locale.ROOT);return visibleOrganizations().stream().filter(v->v.id().equals(value)||v.displayName().equals(input)).findFirst().orElse(null);}
    private Throwable root(Throwable value){while(value.getCause()!=null)value=value.getCause();return value;}
    private void trialStarted(Player player, com.murimrpg.service.HuashanResult result, Throwable failure) { if (failure != null) { error(player, root(failure).getMessage()); return; } info(player, result.message()); trialSpawner.schedule(player, result.state()).ifPresent(target -> info(player, contentCatalog.huashanSettings().trialSpawnDelaySeconds() + "초 후 " + target + "이(가) 소환됩니다.")); }
    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){ List<String> values=switch(command.getName()){case "gangho"->ROOT;case "gangho_sect"->args.length==1?SECT:args.length==2&&List.of("가입","정보").contains(args[0])?visibleOrganizations().stream().map(OrganizationDefinition::displayName).toList():List.of();case "gangho_martial"->args.length==1?MARTIAL:args.length==2?catalog.martialArts().stream().map(GanghoCommandCatalog.Entry::displayName).toList():List.of();case "gangho_qi"->List.of("정보","심법","운기조식");case "gangho_realm"->List.of("정보","돌파");case "gangho_admin"->List.of("유저","문파","무공","경지","내공","퀘스트","화산파");case "gangho_dev"->List.of("data","stat","reset","sect","martial","quest","effect");default->List.of();};String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
