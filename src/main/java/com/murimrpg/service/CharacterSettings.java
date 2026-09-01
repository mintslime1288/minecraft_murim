package com.murimrpg.service;

import com.murimrpg.domain.Family;
import com.murimrpg.infrastructure.config.YamlDocument;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public record CharacterSettings(int maxRerolls, Map<Family, Integer> strengthBonuses,
                                Map<Family, Integer> speedBonuses, Map<Family, Integer> appearanceWeights,
                                Map<String, Integer> talentWeights, Map<String, Integer> constitutionWeights,
                                int cheonmuMinimumPopulation) {
    public CharacterSettings {
        strengthBonuses = Map.copyOf(strengthBonuses); speedBonuses = Map.copyOf(speedBonuses);
        appearanceWeights = Map.copyOf(appearanceWeights); talentWeights = Map.copyOf(talentWeights);
        constitutionWeights = Map.copyOf(constitutionWeights);
    }
    public static CharacterSettings load(Path path) {
        YamlDocument c = YamlDocument.load(path);
        EnumMap<Family,Integer> str = new EnumMap<>(Family.class), spd = new EnumMap<>(Family.class), app = new EnumMap<>(Family.class);
        for (Family family : Family.values()) {
            if (family == Family.STREET) continue;
            String root = "families." + family.id();
            str.put(family, c.integer(root + ".strength-bonus", 0));
            spd.put(family, c.integer(root + ".speed-bonus", 0));
            app.put(family, c.integer(root + ".appearance-weight", 0));
        }
        return new CharacterSettings(c.integer("reroll.max", 5), str, spd, app,
                Map.of("0.75",c.integerIn("talents","0.75",0),"1.0",c.integerIn("talents","1.0",0),
                        "2.0",c.integerIn("talents","2.0",0),"3.0",c.integerIn("talents","3.0",0)),
                Map.of("cheonmu",c.integer("constitutions.cheonmu",0),"mugeuk",c.integer("constitutions.mugeuk",0),"gongryeong",c.integer("constitutions.gongryeong",0),"guyang",c.integer("constitutions.guyang",0),"guum",c.integer("constitutions.guum",0),"mugol",c.integer("constitutions.mugol",0),"ordinary",c.integer("constitutions.ordinary",0)),
                c.integer("cheonmu.minimum-population", 100));
    }
}
