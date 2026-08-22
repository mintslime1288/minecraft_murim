package com.murimrpg.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record PromotionRequirement(String id) {
    private static final Pattern STABLE_ID = Pattern.compile("[a-z][a-z0-9_]*");

    public PromotionRequirement {
        Objects.requireNonNull(id, "id");
        if (!STABLE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid promotion requirement ID: " + id);
        }
    }
}
