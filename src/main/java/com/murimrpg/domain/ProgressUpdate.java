package com.murimrpg.domain;

import java.util.Objects;

public record ProgressUpdate(MajorRealm majorRealm, RealmPhase phase, long cultivationExp) {
    public ProgressUpdate {
        Objects.requireNonNull(majorRealm, "majorRealm");
        Objects.requireNonNull(phase, "phase");
        if (!majorRealm.supports(phase)) {
            throw new IllegalArgumentException(
                    "잘못된 경지 조합입니다: " + majorRealm.id() + " + " + phase.id()
            );
        }
        if (cultivationExp < 0) {
            throw new IllegalArgumentException("수련 EXP는 음수일 수 없습니다.");
        }
    }
}
