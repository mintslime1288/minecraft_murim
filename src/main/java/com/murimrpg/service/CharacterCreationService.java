package com.murimrpg.service;

import com.murimrpg.domain.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** All randomisation and persistence happens here, never inside Bukkit listeners. */
public final class CharacterCreationService {
    private final PlayerProfileService profiles; private final CharacterRollService rolls;
    public CharacterCreationService(PlayerProfileService profiles, CharacterRollService rolls) { this.profiles=profiles; this.rolls=rolls; }
    public CompletableFuture<CharacterData> ensureDraft(UUID uuid) {
        return profiles.loadOrCreate(uuid).thenCompose(profile -> {
            CharacterData data=profile.characterData();
            if(data.completed() || data.draft()!=null) return CompletableFuture.completedFuture(data);
            return roll(uuid, data);
        });
    }
    public CompletableFuture<CharacterData> reroll(UUID uuid) { return profiles.loadOrCreate(uuid).thenCompose(p -> roll(uuid, p.characterData())); }
    private CompletableFuture<CharacterData> roll(UUID uuid, CharacterData current) {
        return profiles.profileCount().thenCombine(profiles.constitutionOwnerCount(Constitution.CHEONMU.id()), (population, owners) -> rolls.createOrReroll(current,population,owners,java.time.Instant.now()))
                .thenCompose(data -> profiles.saveCharacter(uuid,data).thenApply(x -> data));
    }
    public CompletableFuture<CharacterData> confirm(UUID uuid, Family family) {
        return profiles.loadOrCreate(uuid).thenCompose(p -> { CharacterData confirmed=rolls.confirm(p.characterData(),family); return profiles.saveCharacter(uuid,confirmed).thenApply(x -> confirmed); });
    }
    public CompletableFuture<CharacterData> selectFamily(UUID uuid, Family family) { return updateDraft(uuid, draft -> draft.withSelectedFamily(family)); }
    public CompletableFuture<CharacterData> toggleLock(UUID uuid, CharacterTrait trait) { return updateDraft(uuid, draft -> draft.withLockToggled(trait)); }
    private CompletableFuture<CharacterData> updateDraft(UUID uuid, java.util.function.UnaryOperator<CharacterDraft> change) {
        return profiles.loadOrCreate(uuid).thenCompose(profile -> {
            CharacterData current = profile.characterData(); CharacterDraft draft = java.util.Objects.requireNonNull(current.draft(), "No character draft exists");
            CharacterData updated = new CharacterData(false, current.rerollsUsed(), null, 0, 0, 0, null, change.apply(draft));
            return profiles.saveCharacter(uuid, updated).thenApply(ignored -> updated);
        });
    }
    public CompletableFuture<PlayerProfile> reset(UUID uuid) { return profiles.saveCharacter(uuid, CharacterData.uncreated()); }
    public CharacterSettings settings() { return rolls.settings(); }
}
