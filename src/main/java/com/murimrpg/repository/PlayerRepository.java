package com.murimrpg.repository;

import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PlayerContentState;
import com.murimrpg.domain.PlayerJourneyState;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository {
    void initialize();

    Optional<PlayerProfile> findByUuid(UUID uuid);

    PlayerProfile findOrCreate(PlayerProfile defaultProfile);

    void save(PlayerProfile profile);

    default Optional<PlayerContentState> findContentState(UUID uuid) { return Optional.empty(); }
    default void saveContentState(PlayerContentState state) { throw new UnsupportedOperationException("Content state is not supported"); }
    default Optional<PlayerJourneyState> findJourneyState(UUID uuid) { return Optional.empty(); }
    default void saveJourneyState(PlayerJourneyState state) { throw new UnsupportedOperationException("Journey state is not supported"); }

    default int countProfiles() { throw new UnsupportedOperationException("Profile counts are not supported"); }
    default int countCompletedWithConstitution(String constitutionId) { throw new UnsupportedOperationException("Constitution counts are not supported"); }
}
