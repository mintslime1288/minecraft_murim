package com.murimrpg.domain;

public enum OrganizationCategory {
    FIVE_GREAT_FAMILIES,
    NINE_SECTS_ONE_GANG,
    BLACK_PATH,
    HERETICAL_CULT;

    public static OrganizationCategory fromId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown organization category: " + id, exception);
        }
    }
}
