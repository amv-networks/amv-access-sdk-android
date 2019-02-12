package org.amv.access.sdk.hm.bluetooth;

import android.util.Log;

import com.google.common.base.Optional;
import com.highmobility.autoapi.CommandResolver;
import com.highmobility.autoapi.VehicleStatus;
import com.highmobility.hmkit.Link;

import org.amv.access.sdk.hm.AmvSdkSchedulers;
import org.amv.access.sdk.hm.vehicle.HmVehicleState;
import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.bluetooth.BroadcastStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.IncomingCommandEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleConnectionStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.communication.Command;
import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.vehicle.VehicleState;

import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothCommunicationManager implements BluetoothCommunicationManager {
    private static final String TAG = "HmBtleCommunicationMan";

    private final BluetoothBroadcaster broadcaster;
    private final PublishSubject<ConnectionStateChangeEvent> connectionStateSubject;
    private final PublishSubject<IncomingCommandEvent> incomingCommandsSubject;
    private final PublishSubject<VehicleState> vehicleStatusSubject;
    private final PublishSubject<AccessSdkException> incomingFailureSubject;

    private final AtomicReference<BluetoothConnection> connectionRef = new AtomicReference<>();
    private final AtomicReference<HmVehicleState> vehicleState = new AtomicReference<>(HmVehicleState.unknown());
    private volatile Disposable incomingCommandsSubscription;
    private volatile Disposable connectionStateSubscription;
    private volatile Disposable broadcastConnectionSubscription;

    public HmBluetoothCommunicationManager(BluetoothBroadcaster broadcaster) {
        this.broadcaster = checkNotNull(broadcaster);

        this.connectionStateSubject = PublishSubject.create();
        this.incomingCommandsSubject = PublishSubject.create();
        this.vehicleStatusSubject = PublishSubject.create();
        this.incomingFailureSubject = PublishSubject.create();

        this.broadcastConnectionSubscription = this.broadcaster.observeConnections()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler())
                .subscribe(next -> {
                    if (next.isDisconnected()) {
                        onDisconnect();
                    } else if (next.isConnected()) {
                        onConnect(next.getConnection());
                    }
                });
    }

    @Override
    public Observable<Boolean> startConnecting(AccessCertificatePair accessCertificatePair) {
        Log.d(TAG, "startConnecting with cert " + accessCertificatePair.getId());
        disposeSubscriptionsIfNecessary();

        return broadcaster.startBroadcasting(accessCertificatePair);
    }

    @Override
    public Observable<BroadcastStateChangeEvent> observeBroadcastState() {
        return broadcaster.observeBroadcastStateChanges();
    }

    @Override
    public Observable<ConnectionStateChangeEvent> observeConnectionState() {
        return connectionStateSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<IncomingCommandEvent> observeIncomingCommands() {
        return incomingCommandsSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<AccessSdkException> observeIncomingFailureMessages() {
        return incomingFailureSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<VehicleState> observeVehicleState() {
        return vehicleStatusSubject.share()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<Boolean> sendCommand(Command command) {
        checkNotNull(command);

        return activeConnectionOrErr()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler())
                .flatMap(connection -> connection.sendCommand(command));
    }

    /**
     * After the terminate method has been called the instance must not be used again.
     */
    @Override
    public Observable<Boolean> terminate() {
        Observable<Boolean> terminateConnectionAndContinueOnError = Optional
                .fromNullable(connectionRef.get())
                .transform(connection -> connection.terminate())
                .or(Observable.just(true));

        return broadcaster.terminate()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler())
                .flatMap(foo -> terminateConnectionAndContinueOnError)
                .doOnError(e -> terminateInternal())
                .doOnNext(next -> terminateInternal());
    }

    private void terminateInternal() {
        if (broadcastConnectionSubscription != null && !broadcastConnectionSubscription.isDisposed()) {
            broadcastConnectionSubscription.dispose();
        }

        disposeSubscriptionsIfNecessary();
        closeStreamsIfNecessary();

        connectionRef.set(null);
        vehicleState.set(HmVehicleState.unknown());
    }

    private void disposeSubscriptionsIfNecessary() {
        Log.d(TAG, "disposeSubscriptionsIfNecessary");

        if (incomingCommandsSubscription != null && !incomingCommandsSubscription.isDisposed()) {
            incomingCommandsSubscription.dispose();
        }
        if (connectionStateSubscription != null && !connectionStateSubscription.isDisposed()) {
            connectionStateSubscription.dispose();
        }
    }

    private void closeStreamsIfNecessary() {
        Log.d(TAG, "closeStreamsIfNecessary");

        if (!vehicleStatusSubject.hasComplete()) {
            vehicleStatusSubject.onComplete();
        }
        if (!incomingFailureSubject.hasComplete()) {
            incomingFailureSubject.onComplete();
        }
        if (!incomingCommandsSubject.hasComplete()) {
            incomingCommandsSubject.onComplete();
        }
        if (!connectionStateSubject.hasComplete()) {
            connectionStateSubject.onComplete();
        }
    }

    private void onConnect(BluetoothConnection connection) {
        Log.d(TAG, "onConnect");

        disposeSubscriptionsIfNecessary();

        connectionRef.set(connection);
        vehicleState.set(HmVehicleState.unknown());

        SimpleConnectionStateChangeEvent newConnectionStateEvent = SimpleConnectionStateChangeEvent.builder()
                .currentState(HmBluetoothStates.from(Link.State.CONNECTED))
                .previousState(HmBluetoothStates.from(Link.State.DISCONNECTED))
                .build();

        Log.d(TAG, "emit new connection state");
        connectionStateSubject.onNext(newConnectionStateEvent);

        Log.d(TAG, "start observing connecting state");
        connectionStateSubscription = connection.observeConnectionState()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler())
                .subscribe(connectionStateSubject::onNext);

        Log.d(TAG, "start observing incoming commands");
        incomingCommandsSubscription = connection.observeIncomingCommands()
                .subscribeOn(AmvSdkSchedulers.defaultScheduler())
                .doOnNext(incomingCommandsSubject::onNext)
                .doOnNext(this::transformAndPublish)
                .subscribe();
    }

    private void onDisconnect() {
        Log.d(TAG, "onDisconnect");
        disposeSubscriptionsIfNecessary();

        connectionRef.set(null);
        vehicleState.set(HmVehicleState.unknown());
    }

    private void transformAndPublish(IncomingCommandEvent commandEvent) {
        try {
            com.highmobility.autoapi.Command incomingCommand = CommandResolver.resolve(commandEvent.getCommand());
            publishVehicleStateIfEligible(incomingCommand);
        } catch (Exception e) {
            incomingFailureSubject.onNext(AccessSdkException.wrap(e));
        }
    }

    private void publishVehicleStateIfEligible(com.highmobility.autoapi.Command incomingCommand) {
        boolean isVehicleStatusResponse = incomingCommand
                .is(VehicleStatus.TYPE);

        if (isVehicleStatusResponse) {
            // updateAndGet is only available on android >= 24
            vehicleState.set(vehicleState.get().extend(incomingCommand));
            vehicleStatusSubject.onNext(vehicleState.get());
        }
    }

    private Observable<BluetoothConnection> activeConnectionOrErr() {
        return Observable.just(1)
                .flatMap(foo -> {
                    BluetoothConnection bluetoothConnection = connectionRef.get();
                    if (bluetoothConnection == null) {
                        return Observable.error(new RuntimeException("No connection present"));
                    }

                    return Observable.just(bluetoothConnection);
                }).subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }
}
