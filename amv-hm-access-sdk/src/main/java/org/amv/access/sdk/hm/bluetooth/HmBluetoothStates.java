package org.amv.access.sdk.hm.bluetooth;


import com.highmobility.hmkit.Broadcaster;
import com.highmobility.hmkit.Link;

import org.amv.access.sdk.spi.bluetooth.BroadcastState;
import org.amv.access.sdk.spi.bluetooth.ConnectionState;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleBroadcastState;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleConnectionState;

final class HmBluetoothStates {
    static BroadcastState from(Broadcaster.State state) {
        return SimpleBroadcastState.builder()
                .bluetoothEnabled(state != Broadcaster.State.BLUETOOTH_UNAVAILABLE)
                .broadcasting(state == Broadcaster.State.BROADCASTING)
                .idle(state == Broadcaster.State.IDLE)
                .build();
    }

    static ConnectionState from(Link.State state) {
        boolean authenticated = state == Link.State.AUTHENTICATED;
        boolean connected = authenticated || state == Link.State.CONNECTED;

        return SimpleConnectionState.builder()
                .connected(connected)
                .authenticated(authenticated)
                .build();
    }

    private HmBluetoothStates() {
        throw new UnsupportedOperationException();
    }
}
