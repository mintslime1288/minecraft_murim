package com.murimrpg.domain;

/** Common extension point for future martial-art and mental-method proficiency systems. */
public record ProficiencyContext(double baseAmount, boolean mentalMethod) {
    public ProficiencyContext { if (baseAmount < 0) throw new IllegalArgumentException("baseAmount must not be negative"); }
}
