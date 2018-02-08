package org.amv.access.sdk.hm.bluetooth;

import com.google.common.base.Optional;
import com.highmobility.autoapi.Command.FailureMessage;
import com.highmobility.autoapi.Command.VehicleStatus;
import com.highmobility.autoapi.incoming.Failure;
import com.highmobility.autoapi.incoming.IncomingCommand;
import com.highmobility.hmkit.Link;

import org.amv.access.sdk.hm.vehicle.HmVehicleState;
import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.bluetooth.BroadcastStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.IncomingCommandEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleConnectionStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.communication.Command;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.vehicle.VehicleState;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothCommunicationManager implements BluetoothCommunicationManager {
    private static final String TAG = "HmBluetoothCommunicationManager";

    private final BluetoothBroadcaster broadcaster;
    private final CommandFactory commandFactory;
    private final PublishSubject<ConnectionStateChangeEvent> connectionStateSubject;
    private final PublishSubject<IncomingCommandEvent> incomingCommandsSubject;
    private final PublishSubject<VehicleState> vehicleStatusSubject;
    private final PublishSubject<AccessSdkException> incomingFailureSubject;

    private final AtomicReference<BluetoothConnection> connectionRef = new AtomicReference<>();
    private final AtomicReference<HmVehicleState> vehicleState = new AtomicReference<>(HmVehicleState.unknown());
    private volatile Disposable incomingCommandsSubscription;
    private volatile Disposable connectionStateSubscription;
    private volatile Disposable broadcastConnectionSubscription;

    public HmBluetoothCommunicationManager(BluetoothBroadcaster broadcaster, CommandFactory commandFactory) {
        this.broadcaster = checkNotNull(broadcaster);
        this.commandFactory = checkNotNull(commandFactory);

        this.connectionStateSubject = PublishSubject.create();
        this.incomingCommandsSubject = PublishSubject.create();
        this.vehicleStatusSubject = PublishSubject.create();
        this.incomingFailureSubject = PublishSubject.create();
    }

    @Override
    public Observable<Boolean> startConnecting(AccessCertificatePair accessCertificatePair) {
        disposeSubscriptionsIfNecessary();

        this.broadcastConnectionSubscription = this.broadcaster.observeConnections()
                .subscribe(next -> {
                    if (next.isDisconnected()) {
                        onDisconnect();
                    } else if (next.isConnected()) {
                        onConnect(next.getConnection());
                    }
                });

        return this.broadcaster.startBroadcasting(accessCertificatePair);
    }

    @Override
    public Observable<BroadcastStateChangeEvent> observeBroadcastState() {
        return this.broadcaster.observeBroadcastStateChanges();
    }

    @Override
    public Observable<ConnectionStateChangeEvent> observeConnectionState() {
        return connectionStateSubject.share();
    }

    @Override
    public Observable<IncomingCommandEvent> observeIncomingCommands() {
        return incomingCommandsSubject.share();
    }

    @Override
    public Observable<AccessSdkException> observeIncomingFailureMessages() {
        return incomingFailureSubject.share();
    }

    @Override
    public Observable<VehicleState> observeVehicleState() {
        return vehicleStatusSubject.share();
    }

    @Override
    public Observable<Boolean> sendCommand(Command command) {
        return activeConnectionOrErr()
                .flatMap(connection -> connection.sendCommand(command.getBytes()));
    }

    /**
     * After the terminate method has been called the instance must not be used again.
     */
    @Override
    public Observable<Boolean> terminate() {
        Observable<Boolean> sendDisconnectCommandAndContinueOnError = this
                .sendCommand(this.commandFactory.disconnect())
                .timeout(1, TimeUnit.SECONDS)
                .onErrorReturnItem(true);

        return sendDisconnectCommandAndContinueOnError
                .flatMap(foo -> this.broadcaster.terminate())
                .timeout(1, TimeUnit.SECONDS)
                .doOnError(e -> terminateInternal())
                .doOnNext(next -> terminateInternal());
    }

    private void terminateInternal() {
        disposeSubscriptionsIfNecessary();
        closeStreamsIfNecessary();

        connectionRef.set(null);
        vehicleState.set(HmVehicleState.unknown());
    }

    private void disposeSubscriptionsIfNecessary() {
        if (incomingCommandsSubscription != null && !incomingCommandsSubscription.isDisposed()) {
            incomingCommandsSubscription.dispose();
        }
        if (connectionStateSubscription != null && !connectionStateSubscription.isDisposed()) {
            connectionStateSubscription.isDisposed();
        }
        if (broadcastConnectionSubscription != null && !broadcastConnectionSubscription.isDisposed()) {
            broadcastConnectionSubscription.isDisposed();
        }
    }

    private void closeStreamsIfNecessary() {
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
        disposeSubscriptionsIfNecessary();

        connectionRef.set(connection);
        vehicleState.set(HmVehicleState.unknown());

        connectionStateSubject.onNext(SimpleConnectionStateChangeEvent.builder()
                .currentState(HmBluetoothStates.from(Link.State.CONNECTED))
                .previousState(HmBluetoothStates.from(Link.State.DISCONNECTED))
                .build());

        connectionStateSubscription = connection.observeConnectionState()
                .subscribe(connectionStateSubject::onNext);

        incomingCommandsSubscription = connection.observeIncomingCommands()
                .doOnNext(incomingCommandsSubject::onNext)
                .doOnNext(this::transformAndPublish)
                .subscribe();
    }

    private void onDisconnect() {
        disposeSubscriptionsIfNecessary();

        connectionRef.set(null);
        vehicleState.set(HmVehicleState.unknown());

        connectionStateSubject.onNext(SimpleConnectionStateChangeEvent.builder()
                .currentState(HmBluetoothStates.from(Link.State.DISCONNECTED))
                .previousState(HmBluetoothStates.from(Link.State.CONNECTED))
                .build());
    }

    private void transformAndPublish(IncomingCommandEvent commandEvent) {
        Optional<IncomingCommand> incomingCommandOptional = MoreHmCommands
                .parseIncomingCommand(commandEvent.getCommand());
        if (!incomingCommandOptional.isPresent()) {
            return;
        }

        IncomingCommand incomingCommand = incomingCommandOptional.get();

        publishVehicleStateIfEligible(incomingCommand);
        publishFailureIfEligible(incomingCommand);
    }

    private void publishVehicleStateIfEligible(IncomingCommand incomingCommand) {
        boolean isVehicleStatusResponse = incomingCommand
                .is(VehicleStatus.VEHICLE_STATUS);

        if (isVehicleStatusResponse) {
            // updateAndGet is only available on android >= 24
            vehicleState.set(vehicleState.get().extend(incomingCommand));
            vehicleStatusSubject.onNext(vehicleState.get());
        }
    }

    private void publishFailureIfEligible(IncomingCommand incomingCommand) {
        boolean isFailureResponse = incomingCommand.is(FailureMessage.FAILURE_MESSAGE);
        if (isFailureResponse) {
            Failure failure = (Failure) incomingCommand;
            String failureType = failure.getFailedType()
                    .getIdentifier()
                    .name();
            String failureReason = failure.getFailureReason()
                    .name();

            String message = failureType + ": " + failureReason;

            incomingFailureSubject.onNext(AccessSdkException
                    .wrap(new Exception(message)));
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
                });
    }
}
