package com.murimrpg.service;

public enum ProfileLoadState {
    NOT_LOADED("not loaded"),
    LOADING("loading"),
    LOADED("loaded"),
    ERROR("error");

    private final String displayName;

    ProfileLoadState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
