package com.murimrpg.infrastructure.sqlite;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

final class ProfileSetCodec {
    private ProfileSetCodec() {
    }

    static String encode(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining("\n"));
    }

    static Set<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split("\n", -1)).collect(Collectors.toUnmodifiableSet());
    }
}
