package com.murimrpg.service;

import static org.junit.jupiter.api.Assertions.*;
import com.murimrpg.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class CharacterRollServiceTest {
    private static CharacterSettings settings() {
        Map<Family,Integer> zero = new EnumMap<>(Family.class); Map<Family,Integer> offers = new EnumMap<>(Family.class);
        for(Family f:Family.values()) if(f != Family.STREET) { zero.put(f,0); offers.put(f,20); }
        return new CharacterSettings(5,zero,zero,offers,Map.of("0.75",1000,"1.0",5000,"2.0",3000,"3.0",1000),Map.of("cheonmu",1,"mugeuk",200,"gongryeong",299,"guyang",1250,"guum",1250,"mugol",3000,"ordinary",4000),100);
    }
    @Test void rollUsesBaseStatRangeAndPersistentRerollLimit() {
        CharacterRollService service=new CharacterRollService(settings(),new Random(4)); CharacterData data=CharacterData.uncreated();
        for(int i=0;i<6;i++) { data=service.createOrReroll(data,100,0,Instant.EPOCH); assertTrue(data.draft().baseStrength()>=1&&data.draft().baseStrength()<=5); assertTrue(data.draft().baseSpeed()>=1&&data.draft().baseSpeed()<=5); }
        assertEquals(5,data.rerollsUsed()); CharacterData exhausted=data; assertThrows(IllegalStateException.class,()->service.createOrReroll(exhausted,100,0,Instant.EPOCH));
    }
    @Test void confirmationRequiresOfferedFamilyAndKeepsStreetAvailable() {
        CharacterRollService service=new CharacterRollService(settings(),new Random(2)); CharacterData rolled=service.createOrReroll(CharacterData.uncreated(),100,0,Instant.EPOCH);
        CharacterData confirmed=service.confirm(rolled,Family.STREET); assertTrue(confirmed.completed()); assertNull(confirmed.draft());
        Family unoffered=Arrays.stream(Family.values()).filter(f->f!=Family.STREET&&!rolled.draft().offeredFamilies().contains(f)).findFirst().orElse(null);
        if(unoffered!=null) assertThrows(IllegalArgumentException.class,()->service.confirm(rolled,unoffered));
    }
    @Test void cheonmuCapUsesMinimumPopulationPolicy() { CharacterRollService service=new CharacterRollService(settings(),new Random(1)); assertTrue(service.cheonmuAllowed(1,0)); assertFalse(service.cheonmuAllowed(1,1)); assertTrue(service.cheonmuAllowed(200,1)); }
}
