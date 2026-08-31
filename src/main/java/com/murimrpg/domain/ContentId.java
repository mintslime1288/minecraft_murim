package com.murimrpg.domain;

import java.util.regex.Pattern;

public final class ContentId {
    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]*");

    private ContentId() {
    }

    public static String requireValid(String value, String fieldName) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be uppercase snake case: " + value);
        }
        return value;
    }
}
