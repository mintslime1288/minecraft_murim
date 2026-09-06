package com.murimrpg.client;

import com.murimrpg.network.MurimRpgNetworking;
import java.util.Optional;

/** Client-only connection state used by future GLB renderers and effects. */
public final class ClientConnectionState {
    private volatile String serverProtocol;

    public void acceptServerHello(String protocol) {
        serverProtocol = protocol;
    }

    public void clear() {
        serverProtocol = null;
    }

    public boolean isCompatible() {
        return MurimRpgNetworking.PROTOCOL_VERSION.equals(serverProtocol);
    }

    public Optional<String> serverProtocol() {
        return Optional.ofNullable(serverProtocol);
    }
}
