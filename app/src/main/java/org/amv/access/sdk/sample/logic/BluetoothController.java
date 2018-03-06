package org.amv.access.sdk.sample.logic;

import android.content.Context;
import android.util.Log;

import com.google.common.base.Optional;
import com.squareup.leakcanary.RefWatcher;

import org.amv.access.sdk.hm.vehicle.HmVehicleState;
import org.amv.access.sdk.sample.AccessDemoApplication;
import org.amv.access.sdk.sample.R;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.bluetooth.BroadcastState;
import org.amv.access.sdk.spi.bluetooth.BroadcastStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.ConnectionState;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.communication.Command;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.vehicle.VehicleState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.amv.access.sdk.sample.CertificatesActivity.TAG;
import static org.amv.access.sdk.sample.logic.IBluetoothController.State.VEHICLE_READY;
import static org.amv.access.sdk.sample.logic.IBluetoothController.State.VEHICLE_UPDATING;

public class BluetoothController implements IBluetoothController {
    private BluetoothCommunicationManager communicationManager;
    private IBluetoothView view;
    private Context context;
    private AccessSdk accessSdk;

    private final AtomicBoolean initializing = new AtomicBoolean(true);
    private final AtomicLong retryCounter = new AtomicLong(0);
    private final AtomicLong vehicleStateUpdateCounter = new AtomicLong(0);

    private volatile State state;
    private volatile AccessCertificatePair accessCertificatePair;
    private volatile Command.Type sentCommand;
    private volatile AtomicReference<VehicleState> latestVehicleStateRef =
            new AtomicReference<>(HmVehicleState.unknown());

    private volatile Disposable broadcastStateChangesSubscription;
    private volatile Disposable connectionStateChangesSubscription;
    private volatile Disposable incomingFailureSubscription;
    private volatile Disposable vehicleStateSubscription;

    @Override
    public void initialize(IBluetoothView view, Context context) {
        this.view = view;
        this.context = context;

        AmvSdkInitializer.create(context.getApplicationContext())
                .doOnNext(sdk -> this.accessSdk = sdk)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sdk -> {
                    view.onInitializeFinished();
                }, e -> {
                    view.onInitializeFailed(AccessSdkException.wrap(e));
                });
    }

    @Override
    public void connect(String accessCertificateId) {
        checkNotNull(accessCertificateId);

        this.accessCertificatePair = this.accessSdk
                .certificateManager()
                .getAccessCertificates()
                .filter(pair -> accessCertificateId.equals(pair.getId()))
                .blockingFirst();

        view.setVehicleSerial(this.accessCertificatePair
                .getDeviceAccessCertificate()
                .getGainerSerial());

        this.communicationManager = this.accessSdk
                .bluetoothCommunicationManagerFactory()
                .createCommunicationManager();

        this.broadcastStateChangesSubscription = this.communicationManager
                .observeBroadcastState()
                .subscribe(this::onBroadcastStateChanged);

        this.incomingFailureSubscription = this.communicationManager
                .observeIncomingFailureMessages()
                .subscribe(this::onFailureReceived);

        this.vehicleStateSubscription = this.communicationManager
                .observeVehicleState()
                .subscribe(this::onVehicleState);

        this.connectionStateChangesSubscription = this.communicationManager
                .observeConnectionState()
                .subscribe(this::onConnectionStateChanged);

        connect();
    }

    @Override
    public void lockUnlockDoors() {
        if (state != VEHICLE_READY) {
            view.showAlert(context.getString(R.string.bluetooth_module_not_ready_title),
                    context.getString(R.string.bluetooth_module_not_ready_text));
            return;
        }

        VehicleState latestVehicleState = latestVehicleStateRef.get();
        if (!latestVehicleState.getDoorLockState().isPresent()) {
            view.showAlert(context.getString(R.string.door_lock_state_is_unknown),
                    context.getString(R.string.cannot_execute_operation_cancelling));
            return;
        }

        boolean lock = latestVehicleState
                .getDoorLockState()
                .get().isUnlocked();

        CommandFactory commandFactory = accessSdk.commandFactory();
        Command lockDoorsCommand = lock ? commandFactory.lockDoors()
                : commandFactory.unlockDoors();

        this.sentCommand = lockDoorsCommand.getType();

        updateState(VEHICLE_UPDATING);

        this.communicationManager.sendCommand(lockDoorsCommand)
                .subscribe(next -> {
                    Log.d(TAG, "Command successfully sent.");
                }, error -> {
                    view.showAlert(context.getString(R.string.could_not_send_command), error.getMessage());
                    updateState(VEHICLE_READY);
                });
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: ");

        terminateConnectionManager().subscribe(next -> {
            Log.d(TAG, "ConnectionManager terminated.");
        }, error -> {
            Log.e(TAG, "An error occurred", error);
        });

        RefWatcher refWatcher = AccessDemoApplication.getRefWatcher(context);
        refWatcher.watch(this);
        refWatcher.watch(this.communicationManager);
    }

    @Override
    public VehicleState getVehicleState() {
        if (state != VEHICLE_READY) {
            return null;
        }
        return latestVehicleStateRef.get();
    }

    private Observable<Boolean> terminateConnectionManager() {
        if (communicationManager == null) {
            closeStreamsIfNecessary();
            return Observable.just(true);
        } else {
            return communicationManager.terminate()
                    .doOnNext(foo -> {
                        Log.d(TAG, "Disconnect completed.");
                        closeStreamsIfNecessary();
                    })
                    .doOnError(error -> {
                        Log.w(TAG, "Error while disconnecting", error);
                        closeStreamsIfNecessary();
                    })
                    .doOnComplete(() -> Log.d(TAG, "Disconnect successful"));
        }
    }

    private void closeStreamsIfNecessary() {
        if (this.vehicleStateSubscription != null &&
                !this.vehicleStateSubscription.isDisposed()) {
            this.vehicleStateSubscription.dispose();
        }
        if (this.incomingFailureSubscription != null &&
                !this.incomingFailureSubscription.isDisposed()) {
            this.incomingFailureSubscription.dispose();
        }
        if (this.connectionStateChangesSubscription != null &&
                !this.connectionStateChangesSubscription.isDisposed()) {
            this.connectionStateChangesSubscription.dispose();
        }
        if (this.broadcastStateChangesSubscription != null &&
                !this.broadcastStateChangesSubscription.isDisposed()) {
            this.broadcastStateChangesSubscription.dispose();
        }
    }

    private void connect() {
        if (this.accessCertificatePair == null) {
            throw new IllegalStateException("No access certificate chosen");
        }

        communicationManager.startConnecting(accessCertificatePair)
                .subscribe(next -> {
                    Log.d(TAG, "Successfully started broadcasting.");
                }, error -> {
                    view.finishWithMessage(context.getString(R.string.failed_to_start_broadcasting), error.getMessage());
                });
    }


    private void onVehicleState(VehicleState vehicleState) {
        Log.d(TAG, "Vehicle State received: " + vehicleState);

        initializing.set(false);
        retryCounter.set(0L);
        latestVehicleStateRef.set(vehicleState);

        vehicleStateUpdateCounter.getAndIncrement();

        view.onVehicleStatusUpdate(vehicleState);

        updateState(VEHICLE_READY);
    }

    private void updateConnectionStatus(ConnectionState newState) {
        if (newState.isAuthenticated()) {
            updateState(State.VEHICLE_UPDATING);
            requestVehicleStateUpdate();
        } else if (newState.isConnected()) {
            updateState(State.VEHICLE_CONNECTED);
        }
    }

    private void updateState(State state) {
        if (state != this.state) {
            this.state = state;
            view.onStateUpdate(this.state);
        }
    }

    private void requestVehicleStateUpdate() {
        updateState(VEHICLE_UPDATING);

        CommandFactory commandFactory = accessSdk.commandFactory();
        Command command1 = commandFactory.sendVehicleStatus();
        this.sentCommand = command1.getType();

        this.communicationManager.sendCommand(command1)
                .subscribe(next -> {
                    Log.d(TAG, "Command successfully sent.");
                }, error -> {
                    Log.d(TAG, "Error while sending command: " + error.getMessage());
                    if (!initializing.get()) {
                        view.finishWithMessage(context.getString(R.string.cannot_get_vehicle_state), error.getMessage());
                    } else {
                        if (retryCounter.getAndIncrement() == 0L) {
                            requestVehicleStateUpdate();
                        } else {
                            view.finishWithMessage(context.getString(R.string.cannot_get_initial_vehicle_state), error.getMessage());
                        }
                    }
                });
    }

    private void onBroadcastStateChanged(BroadcastStateChangeEvent changeEvent) {
        BroadcastState currentState = changeEvent.getCurrentState();

        Optional<BroadcastState> previousState = changeEvent.getPreviousState();
        if (!currentState.isBluetoothEnabled()) {
            updateState(State.BLE_NOT_AVAILABLE);
        } else {
            if (currentState.isIdle()) {
                updateState(State.IDLE);
                if (previousState.isPresent()) {
                    if (!previousState.get().isBluetoothEnabled()) {
                        connect();
                    }
                }
            } else if (currentState.isBroadcasting()) {
                updateState(State.LOOKING);
            }
        }
    }

    private void onConnectionStateChanged(ConnectionStateChangeEvent changeEvent) {
        ConnectionState currentState = changeEvent.getCurrentState();
        Optional<ConnectionState> previousState = changeEvent.getPreviousState();
        Log.d(TAG, "connection state changed from " + previousState + " to " + currentState);

        updateConnectionStatus(currentState);
    }

    private void onFailureReceived(AccessSdkException error) {
        String alertTitle = context.getString(R.string.failure_response_received);
        Log.d(TAG, "Failure response received: " + error.getMessage());

        if (!initializing.get()) {
            updateState(VEHICLE_READY);
            view.showAlert(alertTitle, error.getMessage());
        } else {
            if (retryCounter.getAndIncrement() == 0L) {
                if (sentCommand.equals(accessSdk.commandFactory().sendVehicleStatus())) {
                    requestVehicleStateUpdate();
                } else {
                    view.showAlert(alertTitle, error.getMessage());
                }
            } else {
                String message = String.format("%s: %s",
                        context.getString(R.string.cannot_get_initial_vehicle_state),
                        error.getMessage());
                view.finishWithMessage(alertTitle, message);
            }
        }
    }
}
