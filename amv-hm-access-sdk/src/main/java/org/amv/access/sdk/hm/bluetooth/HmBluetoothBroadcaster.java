package org.amv.access.sdk.hm.bluetooth;


import android.util.Log;

import com.highmobility.crypto.AccessCertificate;
import com.highmobility.hmkit.Broadcaster;
import com.highmobility.hmkit.ConnectedLink;
import com.highmobility.hmkit.Error.BroadcastError;
import com.highmobility.hmkit.Storage.Result;

import org.amv.access.sdk.spi.bluetooth.BroadcastStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleBroadcastStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothBroadcaster implements BluetoothBroadcaster {
    private static final String TAG = "HmBluetoothBroadcaster";

    private final Broadcaster broadcaster;
    private final PublishSubject<BroadcastStateChangeEvent> broadcasterStateSubject;
    private final PublishSubject<BluetoothConnectionEvent> connectionSubject;

    private final AtomicReference<ConnectedLink> connectedLinkRef = new AtomicReference<>();
    private final AtomicReference<BluetoothConnection> bluetoothConnectionRef = new AtomicReference<>();


    public HmBluetoothBroadcaster(Broadcaster broadcaster) {
        this.broadcaster = checkNotNull(broadcaster);
        this.broadcasterStateSubject = PublishSubject.create();
        this.connectionSubject = PublishSubject.create();
    }

    @Override
    public Observable<Boolean> startBroadcasting(AccessCertificatePair accessCertificatePair) {
        return Observable.create(subscriber -> {
            Log.d(TAG, "startConnecting");

            stopBroadcasting();

            AccessCertificate deviceAccessCertificate = new AccessCertificate(
                    accessCertificatePair.getDeviceAccessCertificate().toByteArray()
            );
            AccessCertificate vehicleAccessCertificate = new AccessCertificate(
                    accessCertificatePair.getVehicleAccessCertificate().toByteArray()
            );

            Log.d(TAG, "register access certificates");

            if (this.broadcaster.registerCertificate(deviceAccessCertificate) != Result.SUCCESS) {
                throw new IllegalStateException("Failed to register certificate to HMKit");
            }

            if (this.broadcaster.storeCertificate(vehicleAccessCertificate) != Result.SUCCESS) {
                throw new IllegalStateException("Failed to store certificate to HMKit");
            }

            this.broadcaster.setBroadcastingTarget(deviceAccessCertificate.getGainerSerial());

            this.broadcaster.setListener(new HmBroadcasterListener());

            Log.d(TAG, "start broadcasting");
            this.broadcaster.startBroadcasting(new Broadcaster.StartCallback() {
                @Override
                public void onBroadcastingStarted() {
                    Log.d(TAG, "broadcasting started");

                    subscriber.onNext(true);
                    subscriber.onComplete();
                }

                @Override
                public void onBroadcastingFailed(BroadcastError broadcastError) {
                    Log.d(TAG, "broadcasting failed");

                    subscriber.onError(new RuntimeException(broadcastError.getType() + ": " + broadcastError.getMessage()));

                    stopBroadcasting();
                }
            });
        });
    }

    private void stopBroadcasting() {
        Log.d(TAG, "stop broadcasting");
        this.broadcaster.stopBroadcasting();
    }

    @Override
    public Observable<BroadcastStateChangeEvent> observeBroadcastStateChanges() {
        return this.broadcasterStateSubject.share();
    }

    @Override
    public Observable<BluetoothConnectionEvent> observeConnections() {
        return this.connectionSubject.share();
    }

    @Override
    public Observable<Boolean> terminate() {
        return Observable.fromCallable(() -> {
            try {
                stopBroadcasting();
            } finally {
                closeStreamsIfNecessary();
            }
            return true;
        });
    }

    private void closeStreamsIfNecessary() {
        if (!connectionSubject.hasComplete()) {
            broadcasterStateSubject.onComplete();
        }
        if (!broadcasterStateSubject.hasComplete()) {
            broadcasterStateSubject.onComplete();
        }
    }

    private class HmBroadcasterListener implements com.highmobility.hmkit.BroadcasterListener {
        @Override
        public void onStateChanged(Broadcaster.State oldState) {
            Log.d(TAG, "broadcaster state changed from "
                    + "'" + oldState + "' to '" + broadcaster.getState() + "'");

            broadcasterStateSubject.onNext(SimpleBroadcastStateChangeEvent.builder()
                    .currentState(HmBluetoothStates.from(broadcaster.getState()))
                    .previousState(HmBluetoothStates.from(oldState))
                    .build());
        }

        @Override
        public void onLinkReceived(ConnectedLink connectedLink) {
            if (isConnectionEstablished()) {
                Log.d(TAG, "received new connection but one already exists - ignoring");
                return;
            }

            Log.d(TAG, "onLinkReceived " + connectedLink.getName());

            BluetoothConnection currentConnection = new HmBluetoothConnection(connectedLink);
            connectedLinkRef.set(connectedLink);
            bluetoothConnectionRef.set(currentConnection);

            connectionSubject.onNext(BluetoothConnectionEvent.connected(currentConnection));
        }

        @Override
        public void onLinkLost(ConnectedLink connectedLink) {
            if (!isActiveConnection(connectedLink)) {
                Log.d(TAG, "unknown connection lost");
                return;
            }

            Log.d(TAG, "onLinkLost " + connectedLink.getName());

            connectedLink.setListener(null);
            connectedLinkRef.set(null);
            BluetoothConnection currentConnection = bluetoothConnectionRef.getAndSet(null);

            connectionSubject.onNext(BluetoothConnectionEvent.disconnected(currentConnection));

            onStateChanged(broadcaster.getState());
        }
    }

    private boolean isActiveConnection(ConnectedLink connectedLink) {
        return connectedLinkRef.get() == connectedLink;
    }

    private boolean isConnectionEstablished() {
        return connectedLinkRef.get() != null;
    }
}
