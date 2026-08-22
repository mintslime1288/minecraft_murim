package com.murimrpg.repository;

import com.murimrpg.domain.PlayerProfile;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository {
    void initialize();

    Optional<PlayerProfile> findByUuid(UUID uuid);

    PlayerProfile findOrCreate(PlayerProfile defaultProfile);

    void save(PlayerProfile profile);
}
