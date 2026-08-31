package com.murimrpg.domain;

import java.util.Objects;

public record PlayerJourney(PlayerContentState content, PlayerJourneyState journey) {
    public PlayerJourney {
        Objects.requireNonNull(content); Objects.requireNonNull(journey);
        if (!content.uuid().equals(journey.uuid())) throw new IllegalArgumentException("Journey UUID mismatch");
    }
}
