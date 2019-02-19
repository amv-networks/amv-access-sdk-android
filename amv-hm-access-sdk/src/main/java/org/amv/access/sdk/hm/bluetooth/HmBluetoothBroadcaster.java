package org.amv.access.sdk.hm.bluetooth;


import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.highmobility.crypto.AccessCertificate;
import com.highmobility.hmkit.BroadcastConfiguration;
import com.highmobility.hmkit.Broadcaster;
import com.highmobility.hmkit.BroadcasterListener;
import com.highmobility.hmkit.ConnectedLink;
import com.highmobility.hmkit.Storage.Result;
import com.highmobility.hmkit.error.BroadcastError;
import com.highmobility.value.Bytes;

import org.amv.access.sdk.hm.AmvSdkSchedulers;
import org.amv.access.sdk.spi.bluetooth.BroadcastStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleBroadcastStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.communication.CommandFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothBroadcaster implements BluetoothBroadcaster {
    private static final String TAG = "HmBluetoothBroadcaster";

    /**
     * Some devices enable an BLE chip power saving mode which causes a connection loss after
     * ~1000ms. This has been observed with models from manufacturer Samsung.
     * To avoid the connection being lost, a keep-alive ping needs to be enabled with an interval
     * lower than 1000ms. 500ms has been verified to be a safe trade-off.
     */
    private static final int IS_ALIVE_PING_INTERVAL_IN_MS = 500;

    private final CommandFactory commandFactory;
    private final Broadcaster broadcaster;
    private final PublishSubject<BroadcastStateChangeEvent> broadcasterStateSubject;
    private final PublishSubject<BluetoothConnectionEvent> connectionSubject;

    private final AtomicReference<ConnectedLink> connectedLinkRef = new AtomicReference<>();
    private final AtomicReference<BluetoothConnection> bluetoothConnectionRef = new AtomicReference<>();
    private final AtomicReference<AccessCertificatePair> currentAccessCertificatePair = new AtomicReference<>();

    public HmBluetoothBroadcaster(CommandFactory commandFactory, Broadcaster broadcaster) {
        this.commandFactory = checkNotNull(commandFactory);
        this.broadcaster = checkNotNull(broadcaster);

        this.broadcasterStateSubject = PublishSubject.create();
        this.connectionSubject = PublishSubject.create();
    }

    /**
     * Starts broadcasting with the given access certificate pair.
     *
     * @param accessCertificatePair The access certificate pair used for communication
     * @return an observable emitting a single truthy boolean value or errors
     */
    @Override
    public Observable<Boolean> startBroadcasting(AccessCertificatePair accessCertificatePair) {
        return Observable.<Boolean>create(subscriber -> {
            Log.d(TAG, "startConnecting");

            stopBroadcasting();
            disconnectAllLinks();

            registerCertificates(accessCertificatePair);

            AccessCertificate deviceAccessCertificate = new AccessCertificate(
                    new Bytes(accessCertificatePair.getDeviceAccessCertificate().toByteArray())
            );

            broadcaster.setListener(new HmBroadcasterListener());

            BroadcastConfiguration broadcastConfig = new BroadcastConfiguration.Builder()
                    .setBroadcastingTarget(deviceAccessCertificate.getGainerSerial())
                    .build();

            Log.d(TAG, "start broadcasting");
            broadcaster.startBroadcasting(new Broadcaster.StartCallback() {
                @Override
                public void onBroadcastingStarted() {
                    Log.d(TAG, "broadcasting started");

                    broadcaster.startAlivePinging(IS_ALIVE_PING_INTERVAL_IN_MS);

                    if (!subscriber.isDisposed()) {
                        subscriber.onNext(true);
                        subscriber.onComplete();
                    }
                }

                @Override
                public void onBroadcastingFailed(BroadcastError broadcastError) {
                    Log.d(TAG, "broadcasting failed");

                    if (!subscriber.isDisposed()) {
                        String errorMessage = broadcastError.getType() + ": " + broadcastError.getMessage();
                        subscriber.onError(new RuntimeException(errorMessage));
                    }
                    stopBroadcasting();
                }
            }, broadcastConfig);
        }).subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<BroadcastStateChangeEvent> observeBroadcastStateChanges() {
        return broadcasterStateSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<BluetoothConnectionEvent> observeConnections() {
        return connectionSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<Boolean> terminate() {
        return Observable.fromCallable(() -> {
            Log.d(TAG, "terminate");
            currentAccessCertificatePair.set(null);

            stopBroadcasting();
            disconnectAllLinks();
            closeStreamsIfNecessary();
            return true;
        }).subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    private void closeStreamsIfNecessary() {
        if (!connectionSubject.hasComplete()) {
            connectionSubject.onComplete();
        }
        if (!broadcasterStateSubject.hasComplete()) {
            broadcasterStateSubject.onComplete();
        }
    }

    private void stopBroadcasting() {
        Log.d(TAG, "stop broadcasting");
        broadcaster.stopAlivePinging();
        broadcaster.stopBroadcasting();
    }

    private boolean isActiveConnection(ConnectedLink connectedLink) {
        return connectedLinkRef.get() == connectedLink;
    }

    private boolean isConnectionEstablished() {
        return connectedLinkRef.get() != null;
    }

    private void disconnectAllLinks() {
        Log.d(TAG, "disconnectAllLinks");

        ConnectedLink connectedLink = connectedLinkRef.getAndSet(null);
        if (connectedLink != null) {
            connectedLink.setListener(null);
        }

        BluetoothConnection currentConnection = bluetoothConnectionRef.getAndSet(null);
        if (currentConnection != null) {
            connectionSubject.onNext(BluetoothConnectionEvent.disconnected(currentConnection));
        }

        broadcaster.disconnectAllLinks();
    }

    private void registerCertificates(AccessCertificatePair accessCertificatePair) {
        currentAccessCertificatePair.set(accessCertificatePair);
        reregisterCurrentCertificates();
    }

    private void reregisterCurrentCertificates() {
        AccessCertificatePair accessCertificatePair = currentAccessCertificatePair.get();
        if (accessCertificatePair == null) {
            return;
        }

        cleanupRegisteredCertificates();

        AccessCertificate deviceAccessCertificate = new AccessCertificate(
                new Bytes(accessCertificatePair.getDeviceAccessCertificate().toByteArray())
        );
        AccessCertificate vehicleAccessCertificate = new AccessCertificate(
                new Bytes(accessCertificatePair.getVehicleAccessCertificate().toByteArray())
        );

        Log.d(TAG, "register device certificate " + deviceAccessCertificate.getGainerSerial().getHex());
        if (broadcaster.registerCertificate(deviceAccessCertificate) != Result.SUCCESS) {
            throw new IllegalStateException("Failed to register certificate to HMKit");
        }

        Log.d(TAG, "register vehicle certificate " + vehicleAccessCertificate.getGainerSerial().getHex());
        if (broadcaster.storeCertificate(vehicleAccessCertificate) != Result.SUCCESS) {
            throw new IllegalStateException("Failed to store certificate to HMKit");
        }
    }

    private void cleanupRegisteredCertificates() {
        Log.d(TAG, "cleanup access certificates");
        for (AccessCertificate knownCertificate : findAllKnownCertificates()) {
            this.broadcaster.revokeCertificate(knownCertificate.getGainerSerial());
            this.broadcaster.revokeCertificate(knownCertificate.getProviderSerial());
        }
    }

    private List<AccessCertificate> findAllKnownCertificates() {
        ImmutableList<AccessCertificate> knownCertificates = ImmutableList.<AccessCertificate>builder()
                .addAll(Arrays.asList(this.broadcaster.getRegisteredCertificates()))
                .addAll(Arrays.asList(this.broadcaster.getStoredCertificates()))
                .build();

        return knownCertificates;
    }

    private class HmBroadcasterListener implements BroadcasterListener {
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
            Log.d(TAG, "onLinkReceived " + connectedLink.getName());

            if (isConnectionEstablished()) {
                Log.d(TAG, "received new connection but one already exists - ignoring");
                return;
            }

            BluetoothConnection currentConnection = new HmBluetoothConnection(commandFactory, connectedLink);
            connectedLinkRef.set(connectedLink);
            bluetoothConnectionRef.set(currentConnection);

            connectionSubject.onNext(BluetoothConnectionEvent.connected(currentConnection));
        }

        @Override
        public void onLinkLost(ConnectedLink connectedLink) {
            // needed, as hmcore deletes certs on successful authenticated connection
            reregisterCurrentCertificates();

            if (!isActiveConnection(connectedLink)) {
                Log.d(TAG, "unknown connection lost");
                return;
            }

            Log.d(TAG, "onLinkLost " + connectedLink.getName());

            connectedLink.setListener(null);
            connectedLinkRef.set(null);

            BluetoothConnection currentConnection = bluetoothConnectionRef.getAndSet(null);
            if (currentConnection != null) {
                connectionSubject.onNext(BluetoothConnectionEvent.disconnected(currentConnection));
            }
        }
    }
}
