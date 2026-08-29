package com.murimrpg.service;

import com.murimrpg.domain.Family;
import java.util.EnumMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

public record CharacterSettings(int maxRerolls, Map<Family, Integer> strengthBonuses,
                                Map<Family, Integer> speedBonuses, Map<Family, Integer> appearanceWeights,
                                Map<String, Integer> talentWeights, Map<String, Integer> constitutionWeights,
                                int cheonmuMinimumPopulation) {
    public CharacterSettings {
        strengthBonuses = Map.copyOf(strengthBonuses); speedBonuses = Map.copyOf(speedBonuses);
        appearanceWeights = Map.copyOf(appearanceWeights); talentWeights = Map.copyOf(talentWeights); constitutionWeights = Map.copyOf(constitutionWeights);
    }
    public static CharacterSettings from(FileConfiguration c) {
        EnumMap<Family,Integer> str = new EnumMap<>(Family.class), spd = new EnumMap<>(Family.class), app = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            if (family == Family.STREET) continue;
            String path = "families." + family.id();
            str.put(family, c.getInt(path + ".strength-bonus")); spd.put(family, c.getInt(path + ".speed-bonus")); app.put(family, c.getInt(path + ".appearance-weight"));
        }
        return new CharacterSettings(c.getInt("reroll.max", 5), str, spd, app,
                Map.of("0.75",c.getInt("talents.0.75"),"1.0",c.getInt("talents.1.0"),"2.0",c.getInt("talents.2.0"),"3.0",c.getInt("talents.3.0")),
                Map.of("cheonmu",c.getInt("constitutions.cheonmu"),"mugeuk",c.getInt("constitutions.mugeuk"),"gongryeong",c.getInt("constitutions.gongryeong"),"guyang",c.getInt("constitutions.guyang"),"guum",c.getInt("constitutions.guum"),"mugol",c.getInt("constitutions.mugol"),"ordinary",c.getInt("constitutions.ordinary")), c.getInt("cheonmu.minimum-population", 100));
    }
}
