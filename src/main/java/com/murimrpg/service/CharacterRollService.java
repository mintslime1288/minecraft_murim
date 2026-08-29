package com.murimrpg.service;

import com.murimrpg.domain.*;
import java.time.Instant;
import java.util.*;
import java.util.random.RandomGenerator;

public final class CharacterRollService {
    private final CharacterSettings settings; private final RandomGenerator random;
    public CharacterRollService(CharacterSettings settings, RandomGenerator random) { this.settings=settings; this.random=random; validate(); }
    public CharacterData createOrReroll(CharacterData current, int knownProfiles, int cheonmuOwners, Instant ignored) {
        if (current.completed()) throw new IllegalStateException("Character creation is already completed");
        boolean reroll = current.draft() != null;
        if (reroll && current.rerollsUsed() >= settings.maxRerolls()) throw new IllegalStateException("No rerolls remain");
        int used = current.rerollsUsed() + (reroll ? 1 : 0);
        CharacterDraft previous = current.draft(); Set<CharacterTrait> locks = previous == null ? Set.of() : previous.lockedTraits();
        Set<Family> offers = locks.contains(CharacterTrait.FAMILY) ? previous.offeredFamilies() : EnumSet.noneOf(Family.class);
        if (!locks.contains(CharacterTrait.FAMILY)) for (Family f : Family.values()) if (f != Family.STREET && random.nextInt(100) < settings.appearanceWeights().get(f)) offers.add(f);
        Constitution constitution = locks.contains(CharacterTrait.CONSTITUTION) ? previous.constitution() : Constitution.fromId(weighted(settings.constitutionWeights()));
        if (constitution == Constitution.CHEONMU && !cheonmuAllowed(knownProfiles, cheonmuOwners)) constitution = Constitution.ORDINARY;
        CharacterDraft draft = new CharacterDraft(offers, previous == null ? Family.STREET : previous.selectedFamily(), locks.contains(CharacterTrait.STRENGTH) ? previous.baseStrength() : random.nextInt(1, 6), locks.contains(CharacterTrait.SPEED) ? previous.baseSpeed() : random.nextInt(1, 6), locks.contains(CharacterTrait.TALENT) ? previous.talentMultiplier() : Double.parseDouble(weighted(settings.talentWeights())), constitution, locks);
        return new CharacterData(false, used, null, 0, 0, 0, null, draft);
    }
    public CharacterData confirm(CharacterData current, Family family) {
        CharacterDraft draft = Objects.requireNonNull(current.draft(), "No character draft exists");
        if (family != Family.STREET && !draft.offeredFamilies().contains(family)) throw new IllegalArgumentException("That family was not offered");
        return new CharacterData(true,current.rerollsUsed(),family,draft.baseStrength(),draft.baseSpeed(),draft.talentMultiplier(),draft.constitution(),null);
    }
    public boolean cheonmuAllowed(int profiles, int owners) { return owners < Math.max(1, (int)Math.floor(Math.max(profiles, settings.cheonmuMinimumPopulation()) / 100.0)); }
    public CharacterSettings settings() { return settings; }
    private String weighted(Map<String,Integer> weights) { int total=weights.values().stream().mapToInt(Integer::intValue).sum(); int pick=random.nextInt(total); for(var e:weights.entrySet()){pick-=e.getValue();if(pick<0)return e.getKey();} throw new IllegalStateException("unreachable"); }
    private void validate() { if(settings.talentWeights().values().stream().mapToInt(Integer::intValue).sum()!=10000 || settings.constitutionWeights().values().stream().mapToInt(Integer::intValue).sum()!=10000) throw new IllegalArgumentException("Talent and constitution weights must total 10000"); }
}
