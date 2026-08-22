package com.murimrpg.service;

import com.murimrpg.domain.PlayerProfile;
import java.util.Objects;

public record ProfileMutation<T>(PlayerProfile profile, T result, boolean save) {
    public ProfileMutation {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(result, "result");
    }

    public static <T> ProfileMutation<T> saved(PlayerProfile profile, T result) {
        return new ProfileMutation<>(profile, result, true);
    }

    public static <T> ProfileMutation<T> unchanged(PlayerProfile profile, T result) {
        return new ProfileMutation<>(profile, result, false);
    }
}
