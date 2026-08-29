package com.murimrpg.ui;

import com.murimrpg.domain.*;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.PlayerProfileService;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;

/** Presentation only: selection is held for an open screen, rolls are saved by CharacterCreationService. */
public final class CharacterCreationGui implements Listener {
    private static final String TITLE = "강호이문록 | 운명 결정";
    private final CharacterCreationService characters; private final PlayerProfileService profiles; private final Executor main; private final Logger logger;
    public CharacterCreationGui(CharacterCreationService characters, PlayerProfileService profiles, Executor main, Logger logger) { this.characters=characters;this.profiles=profiles;this.main=main;this.logger=logger; }
    public void openIfNeeded(Player player) {
        characters.ensureDraft(player.getUniqueId()).whenCompleteAsync((data,error)-> { if(error!=null){logger.log(Level.SEVERE,"Character draft creation failed",error);return;} if(!data.completed()&&player.isOnline()) open(player,data); },main);
    }
    public void forget(UUID uuid) { }
    private void open(Player p, CharacterData data) {
        CharacterDraft draft=data.draft(); Family family=draft.selectedFamily();
        if(family != Family.STREET && !draft.offeredFamilies().contains(family)) family=Family.STREET;
        Inventory inv=Bukkit.createInventory(new Holder(),45,Component.text(TITLE));
        inv.setItem(4,item(Material.NETHER_STAR,"당신의 운명이 결정되었습니다.",List.of("가문을 선택한 뒤 확정하세요.")));
        int slot=10; for(Family f: Family.values()) { if(f==Family.STREET || draft.offeredFamilies().contains(f)) inv.setItem(slot++,item(f==family?Material.LIME_DYE:Material.PAPER,(f==Family.STREET?"길거리 출신":f.displayName()),List.of(f==family?"선택됨":"클릭하여 선택"))); }
        int strengthBonus=family==Family.STREET?0: characters.settings().strengthBonuses().get(family); int speedBonus=family==Family.STREET?0:characters.settings().speedBonuses().get(family);
        inv.setItem(28,item(Material.IRON_INGOT,"힘: "+draft.baseStrength()+" ("+(strengthBonus>=0?"+":"")+strengthBonus+") → "+(draft.baseStrength()+strengthBonus),List.of("기본값 / 가문 보정 / 최종값")));
        inv.setItem(29,item(Material.SUGAR,"속도: "+draft.baseSpeed()+" ("+(speedBonus>=0?"+":"")+speedBonus+") → "+(draft.baseSpeed()+speedBonus),List.of("기본값 / 가문 보정 / 최종값")));
        inv.setItem(30,item(Material.EXPERIENCE_BOTTLE,"재능: "+(int)(draft.talentMultiplier()*100)+"%",List.of("숙련도 획득 배율")));
        inv.setItem(31,item(Material.HEART_OF_THE_SEA,"체질: "+draft.constitution().displayName(),List.of(draft.constitution().description())));
        CharacterTrait[] traits = CharacterTrait.values();
        for (int i = 0; i < traits.length; i++) {
            CharacterTrait trait = traits[i]; boolean locked = draft.lockedTraits().contains(trait);
            inv.setItem(33 + i, item(locked ? Material.TRIPWIRE_HOOK : Material.LEVER,
                    traitName(trait) + (locked ? " 잠금" : " 잠금 해제"), List.of("클릭: 리롤 시 " + (locked ? "유지" : "다시 결정"))));
        }
        inv.setItem(40,item(Material.EMERALD_BLOCK,"확정",List.of("이 선택은 영구 저장됩니다.")));
        inv.setItem(42,item(data.rerollsUsed() >= characters.settings().maxRerolls() ? Material.BARRIER : Material.REDSTONE,"다시 뽑기",List.of("남은 리롤: "+(characters.settings().maxRerolls()-data.rerollsUsed())+"회")));
        p.openInventory(inv);
    }
    private ItemStack item(Material m,String name,List<String> lore){ ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.displayName(Component.text(name));meta.lore(lore.stream().map(Component::text).toList());i.setItemMeta(meta);return i; }
    @EventHandler public void click(InventoryClickEvent e) { if(!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p))return; e.setCancelled(true); int slot=e.getRawSlot(); if(slot<0||slot>=45)return;
        characters.ensureDraft(p.getUniqueId()).thenAcceptAsync(data -> { if(data.completed())return; CharacterDraft d=data.draft(); List<Family> families=Arrays.stream(Family.values()).filter(f -> f==Family.STREET || d.offeredFamilies().contains(f)).toList(); int idx=slot-10;
            if(idx>=0&&idx<families.size()){characters.selectFamily(p.getUniqueId(),families.get(idx)).thenAcceptAsync(updated -> open(p,updated),main);return;}
            if(slot>=33&&slot<=37){characters.toggleLock(p.getUniqueId(),CharacterTrait.values()[slot-33]).thenAcceptAsync(updated -> open(p,updated),main);return;}
            if(slot==42){ characters.reroll(p.getUniqueId()).whenCompleteAsync((rolled,error)->{if(error!=null)p.sendMessage(Component.text("리롤할 수 없습니다: "+root(error).getMessage()));else open(p,rolled);},main); }
            if(slot==40){Family f=d.selectedFamily();characters.confirm(p.getUniqueId(),f).whenCompleteAsync((done,error)->{if(error!=null){p.sendMessage(Component.text("확정 실패: "+root(error).getMessage()));return;}p.closeInventory();p.sendMessage(Component.text("캐릭터 생성이 완료되었습니다: "+done.family().displayName()));},main);}
        },main); }
    private Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
    private String traitName(CharacterTrait trait) { return switch (trait) { case FAMILY -> "가문"; case TALENT -> "재능"; case STRENGTH -> "힘"; case SPEED -> "속도"; case CONSTITUTION -> "체질"; }; }
    private static final class Holder implements InventoryHolder { @Override public Inventory getInventory(){return null;} }
}
