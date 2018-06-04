package org.amv.access.sdk.sample.logic;

import android.content.Context;

import org.amv.access.sdk.spi.vehicle.VehicleState;

public interface IBluetoothController {
    enum State {
        BLE_NOT_AVAILABLE, // BLE is not available. check if BLE 4.0 is enabled for this device and turned on
        IDLE, // BluetoothBroadcaster is idle. It should not be in this state
        LOOKING, // BluetoothBroadcaster is broadcasting and accepting vehicle connections
        VEHICLE_CONNECTED, // A vehicle has connected to the broadcaster
        VEHICLE_UPDATING, // A command is being sent to the vehicle. No interaction with the controller should be made in this state.
        // Preferably a progress bar should be shown
        VEHICLE_READY // Vehicle is connected and ready to receive commands
    }

    void initialize(IBluetoothView view, Context context);

    /**
     * @param accessCertificateId the access certificate id
     * @throws IllegalStateException When the HMKit is not initialized with a device certificate
     */
    void connect(String accessCertificateId);

    /**
     * Lock or unlock the doors according to the current state.
     */
    void lockUnlockDoors();

    /**
     * Request current vehicle state.
     */
    void requestVehicleState();

    /**
     * Request current vehicle state.
     */
    void sendDisconnectCommand();

    /**
     * Call this when the activity is destroyed
     */
    void onDestroy();
}
