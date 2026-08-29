package com.murimrpg.repository;

import com.murimrpg.domain.PlayerProfile;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository {
    void initialize();

    Optional<PlayerProfile> findByUuid(UUID uuid);

    PlayerProfile findOrCreate(PlayerProfile defaultProfile);

    void save(PlayerProfile profile);

    default int countProfiles() { throw new UnsupportedOperationException("Profile counts are not supported"); }
    default int countCompletedWithConstitution(String constitutionId) { throw new UnsupportedOperationException("Constitution counts are not supported"); }
}
