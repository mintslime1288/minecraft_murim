package com.murimrpg.domain;

public enum OrganizationType {
    FAMILY,
    SECT,
    BLACK_PATH_GROUP,
    CULT;

    public static OrganizationType fromId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown organization type: " + id, exception);
        }
    }
}
