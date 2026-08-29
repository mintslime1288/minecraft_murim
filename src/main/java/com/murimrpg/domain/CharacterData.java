package com.murimrpg.domain;

import java.util.Set;

/** Persisted initial-character state; a null draft means no unfinished roll. */
public record CharacterData(boolean completed, int rerollsUsed, Family family, int baseStrength,
                            int baseSpeed, double talentMultiplier, Constitution constitution,
                            CharacterDraft draft) {
    public CharacterData {
        if (rerollsUsed < 0 || rerollsUsed > 5) throw new IllegalArgumentException("Invalid reroll count");
        if (completed && draft != null) throw new IllegalArgumentException("Completed character cannot retain a draft");
        if (completed && (family == null || constitution == null)) throw new IllegalArgumentException("Completed character data is incomplete");
    }
    public static CharacterData uncreated() { return new CharacterData(false, 0, null, 0, 0, 0, null, null); }
    public Set<String> capabilities() { return completed ? family.capabilities() : Set.of(); }
}
