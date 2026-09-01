package com.murimrpg.ui;

public enum MartialArtStatus {
    READY("준비"), COOLDOWN("쿨다운"), INSUFFICIENT_QI("내공 부족"), LOCKED("미해금"), CONDITION_UNMET("조건 불충족");
    private final String displayName;
    MartialArtStatus(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}
