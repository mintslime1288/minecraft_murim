package com.murimrpg.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.murimrpg.network.MurimRpgNetworking;
import org.junit.jupiter.api.Test;

class ClientConnectionStateTest {
    @Test void reportsCompatibilityOnlyForTheCurrentProtocol() {
        ClientConnectionState state = new ClientConnectionState();
        assertFalse(state.isCompatible());

        state.acceptServerHello(MurimRpgNetworking.PROTOCOL_VERSION);
        assertTrue(state.isCompatible());

        state.clear();
        assertFalse(state.isCompatible());
    }
}
