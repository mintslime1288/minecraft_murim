package com.murimrpg.service;

import com.murimrpg.domain.*;

/** Central calculation hooks for future combat, skill, meditation and proficiency services. */
public final class CharacterModifierService {
    public double proficiencyMultiplier(CharacterData data, ProficiencyContext context) {
        if (!data.completed()) return 1.0;
        double constitution = switch(data.constitution()) { case CHEONMU -> 3.0; case MUGOL -> 1.25; case MUGEUK -> context.mentalMethod() ? 1.5 : 1.0; default -> 1.0; };
        return data.talentMultiplier() * constitution;
    }
    public double incomingDamageMultiplier(CharacterData data) { return data.completed() && data.family()==Family.SACHUN_DANG ? 1.25 : 1.0; }
    public double swordDamageMultiplier(CharacterData data) { if(!data.completed()) return 1; return data.family()==Family.JEGA ? .75 : data.family()==Family.NAMGUNG ? 1.0 : 1.0; }
    public boolean canUseInsufficientQiSkill(CharacterData data) { return data.completed() && data.constitution()==Constitution.GONGRYEONG; }
    public double insufficientQiSkillMultiplier(CharacterData data) { return canUseInsufficientQiSkill(data) ? .75 : 1; }
}
