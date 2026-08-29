package com.murimrpg.command;

import com.murimrpg.domain.GanghoCommandCatalog;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
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
    public GanghoCommand(GanghoCommandCatalog catalog, PlayerProfileService profiles, ProgressionService progression,
            CharacterCreationService characters, CharacterCreationGui characterGui, Executor mainThread,
            GanghoUiManager uiManager) {
        this.catalog = catalog; this.profiles = profiles; this.progression = progression; this.characters = characters; this.characterGui = characterGui; this.mainThread = mainThread; this.uiManager = uiManager;
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
    private boolean root(Player player, String[] args) { if (args.length == 0) { help(player); return true; } switch (args[0]) { case "도움말" -> help(player); case "내정보", "상태" -> profile(player); case "설정" -> characterGui.openIfNeeded(player); case "공지" -> info(player, "현재 공지가 없습니다."); default -> error(player, "사용법: /강호 <도움말|내정보|상태|설정|공지>"); } return true; }
    private boolean profile(Player player) { profiles.loadOrCreate(player.getUniqueId()).whenCompleteAsync((profile, error) -> {
        if (error != null) { error(player, "인물정보를 불러오지 못했습니다."); return; }
        if (!uiManager.openProfile(player, profile)) {
            info(player, "먼저 운명 결정을 완료하세요.");
            characterGui.openIfNeeded(player);
        }
    }, mainThread); return true; }
    private void showProfile(CommandSender sender, PlayerProfile profile) { var character = profile.characterData(); info(sender, "━━━ 강호이문록 · 인물정보 ━━━"); info(sender, "경지: " + profile.realmDisplayName() + " | 수련: " + profile.cultivationExp()); info(sender, "가문: " + (character.completed() ? character.family().displayName() : "미결정") + " | 체질: " + (character.completed() ? character.constitution().displayName() : "미결정")); if (character.completed()) info(sender, "기초 힘/신법: " + character.baseStrength() + "/" + character.baseSpeed() + " | 재능: x" + character.talentMultiplier()); info(sender, "문파·무공·내공: 미구현"); }
    private boolean sect(Player player, String[] args) { if (args.length == 0) { ready(player, "문파"); return true; } if (args[0].equals("목록")) info(player, "등록 문파: " + catalog.organizations().stream().map(GanghoCommandCatalog.Entry::displayName).collect(java.util.stream.Collectors.joining(", "))); else if (args[0].equals("정보") && args.length == 2) catalog.organization(args[1]).ifPresentOrElse(entry -> info(player, entry.displayName() + ": " + entry.description()), () -> error(player, "존재하지 않는 문파입니다.")); else if (args[0].equals("가입") && args.length == 2) catalog.organization(args[1]).ifPresentOrElse(entry -> info(player, entry.displayName() + " 입문 시험은 현재 준비 중입니다. 즉시 가입되지 않습니다."), () -> error(player, "존재하지 않는 문파입니다.")); else if (SECT.contains(args[0])) ready(player, "문파 " + args[0]); else error(player, "사용법: /문파 <정보|목록|가입|탈퇴|위치|내정보|계급|승급|공헌도|임무|상점|무공>"); return true; }
    private boolean martial(Player player, String[] args) { if (args.length == 0) { ready(player, "무공"); return true; } if (args[0].equals("목록") || MARTIAL.contains(args[0])) info(player, "등록 무공: " + catalog.martialArts().stream().map(GanghoCommandCatalog.Entry::displayName).collect(java.util.stream.Collectors.joining(", "))); else if (args[0].equals("정보") && args.length == 2) catalog.martialArt(args[1]).ifPresentOrElse(entry -> info(player, entry.displayName() + ": " + entry.description()), () -> error(player, "존재하지 않는 무공입니다.")); else if (args[0].equals("장착") || args[0].equals("해제") || args[0].equals("숙련도")) ready(player, "무공 " + args[0]); else error(player, "사용법: /무공 <목록|정보|장착|해제|숙련도|심법|검법|도법|권법|장법|지법|경공|절기>"); return true; }
    private boolean qi(Player player, String[] args) { if (args.length == 0 || args[0].equals("정보") || args[0].equals("심법")) ready(player, "내공"); else if (args[0].equals("운기조식")) ready(player, "운기조식"); else error(player, "사용법: /내공 <정보|심법|운기조식>"); return true; }
    private boolean realm(Player player, String[] args) { if (args.length > 0 && args[0].equals("돌파")) { progression.promote(player.getUniqueId()).whenCompleteAsync((result, error) -> { if(error != null) error(player,"돌파 처리에 실패했습니다."); else info(player, result.status().name()); }, mainThread); } else profile(player); return true; }
    private boolean admin(CommandSender sender, String[] args) { if (!sender.hasPermission("gangho.admin")) { error(sender,"권한이 없습니다: gangho.admin"); return true; } if (args.length == 3 && args[0].equals("유저") && args[1].equals("정보")) { Player target = Bukkit.getPlayerExact(args[2]); if(target == null) error(sender,"온라인 플레이어를 찾을 수 없습니다."); else profiles.loadOrCreate(target.getUniqueId()).whenCompleteAsync((profile,error)-> { if(error != null) error(sender,"조회 실패"); else showProfile(sender,profile); },mainThread); } else info(sender,"사용법: /강호관리 유저 정보 <플레이어> (그 외 문파/무공/퀘스트 변경은 해당 서비스 구현 후 활성화됩니다.)"); return true; }
    private boolean dev(CommandSender sender, String[] args) { if (!sender.hasPermission("gangho.dev")) { error(sender,"권한이 없습니다: gangho.dev"); return true; } Player player=player(sender); if(player == null) return true; if(args.length == 1 && args[0].equals("reset")) characters.reset(player.getUniqueId()).whenCompleteAsync((result,error)-> { if(error != null) error(player,"초기화 실패"); else { info(player,"최초 입장 스탯을 초기화했습니다."); characterGui.openIfNeeded(player); } },mainThread); else if(args.length == 1 && (args[0].equals("data") || args[0].equals("stat"))) profile(player); else info(player,"사용법: /강호dev <data|stat|reset|sect|martial|quest|effect>"); return true; }
    private void help(CommandSender sender) { info(sender,"━━━ 강호이문록 ━━━"); for(String line:List.of("[캐릭터] /내정보 /경지","[문파] /문파","[무공] /무공 /내공","[모험] /퀘스트 /기연 /도감 /지도","[도움말] /강호 도움말")) info(sender,line); }
    private boolean ready(Player player,String feature){ info(player,feature+" 시스템은 현재 준비 중입니다."); return true; }
    private Player player(CommandSender sender){ if(sender instanceof Player player)return player; error(sender,"플레이어만 사용할 수 있는 명령어입니다."); return null; }
    private void info(CommandSender sender,String text){sender.sendMessage(Component.text("[강호이문록] "+text,NamedTextColor.YELLOW));} private void error(CommandSender sender,String text){sender.sendMessage(Component.text("[강호이문록] "+text,NamedTextColor.RED));}
    @Override public List<String> onTabComplete(@NotNull CommandSender sender,@NotNull Command command,@NotNull String alias,@NotNull String[] args){ List<String> values=switch(command.getName()){case "gangho"->ROOT;case "gangho_sect"->args.length==1?SECT:args.length==2&&List.of("가입","정보").contains(args[0])?catalog.organizations().stream().map(GanghoCommandCatalog.Entry::displayName).toList():List.of();case "gangho_martial"->args.length==1?MARTIAL:args.length==2?catalog.martialArts().stream().map(GanghoCommandCatalog.Entry::displayName).toList():List.of();case "gangho_qi"->List.of("정보","심법","운기조식");case "gangho_realm"->List.of("정보","돌파");case "gangho_admin"->List.of("유저","문파","무공","경지","내공","퀘스트");case "gangho_dev"->List.of("data","stat","reset","sect","martial","quest","effect");default->List.of();};String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return values.stream().filter(value->value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();}
}
