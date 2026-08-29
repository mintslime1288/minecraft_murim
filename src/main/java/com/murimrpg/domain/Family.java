package com.murimrpg.domain;

import java.util.Set;

/** Character origin. Values which affect balance are supplied by character.yml. */
public enum Family {
    STREET("street", "길거리 출신", Set.of()),
    NAMGUNG("namgung", "남궁세가", Set.of("SWORD_AFFINITY")),
    SACHUN_DANG("sachun_dang", "사천당가", Set.of("CAN_USE_HIDDEN_WEAPON", "CAN_USE_POISON_ART")),
    JEGA("jega", "제갈세가", Set.of("CAN_USE_FORMATION")),
    HABUK_PENG("habuk_peng", "하북팽가", Set.of()),
    MOYONG("moyong", "모용세가", Set.of("DAMAGE_REFLECTION"));

    private final String id;
    private final String displayName;
    private final Set<String> capabilities;

    Family(String id, String displayName, Set<String> capabilities) {
        this.id = id; this.displayName = displayName; this.capabilities = capabilities;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public Set<String> capabilities() { return capabilities; }
    public static Family fromId(String id) {
        for (Family value : values()) if (value.id.equalsIgnoreCase(id)) return value;
        throw new IllegalArgumentException("Unknown family: " + id);
    }
}
