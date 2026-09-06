package com.murimrpg.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CurrencyTypeTest {
    @Test void keepsStableIdsAndRecognizesLegacyPaperIds() {
        assertEquals(CurrencyType.EUNWONBO, CurrencyType.fromId("EUNHWA").orElseThrow());
        assertEquals(CurrencyType.GEUMWONBO, CurrencyType.fromId("geumwonbo").orElseThrow());
    }
}
