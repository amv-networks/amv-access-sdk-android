package org.amv.access.sdk.hm.bluetooth;

import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.IncomingCommandEvent;
import org.amv.access.sdk.spi.communication.Command;

import io.reactivex.Observable;

public interface BluetoothConnection {
    Observable<ConnectionStateChangeEvent> observeConnectionState();

    Observable<IncomingCommandEvent> observeIncomingCommands();

    Observable<Boolean> sendCommand(Command command);

    Observable<Boolean> terminate();

}
