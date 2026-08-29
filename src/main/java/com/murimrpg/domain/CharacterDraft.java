package com.murimrpg.domain;

import java.util.Set;

public record CharacterDraft(Set<Family> offeredFamilies, Family selectedFamily, int baseStrength, int baseSpeed,
                             double talentMultiplier, Constitution constitution, Set<CharacterTrait> lockedTraits) {
    public CharacterDraft {
        offeredFamilies = Set.copyOf(offeredFamilies);
        lockedTraits = Set.copyOf(lockedTraits);
        if (selectedFamily != Family.STREET && !offeredFamilies.contains(selectedFamily)) throw new IllegalArgumentException("Selected family was not offered");
        if (baseStrength < 1 || baseStrength > 5 || baseSpeed < 1 || baseSpeed > 5) throw new IllegalArgumentException("Base stats must be 1..5");
        if (talentMultiplier <= 0) throw new IllegalArgumentException("Talent must be positive");
    }
    public CharacterDraft(Set<Family> offeredFamilies, int baseStrength, int baseSpeed, double talentMultiplier, Constitution constitution) {
        this(offeredFamilies, Family.STREET, baseStrength, baseSpeed, talentMultiplier, constitution, Set.of());
    }
    public CharacterDraft withSelectedFamily(Family family) { return new CharacterDraft(offeredFamilies, family, baseStrength, baseSpeed, talentMultiplier, constitution, lockedTraits); }
    public CharacterDraft withLockToggled(CharacterTrait trait) {
        java.util.EnumSet<CharacterTrait> locks = lockedTraits.isEmpty() ? java.util.EnumSet.noneOf(CharacterTrait.class) : java.util.EnumSet.copyOf(lockedTraits);
        if (!locks.add(trait)) locks.remove(trait);
        return new CharacterDraft(offeredFamilies, selectedFamily, baseStrength, baseSpeed, talentMultiplier, constitution, locks);
    }
}
