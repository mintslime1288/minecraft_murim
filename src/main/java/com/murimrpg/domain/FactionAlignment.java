package com.murimrpg.domain;

public enum FactionAlignment {
    ORTHODOX,
    UNORTHODOX;

    public static FactionAlignment fromId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown faction alignment: " + id, exception);
        }
    }
}
