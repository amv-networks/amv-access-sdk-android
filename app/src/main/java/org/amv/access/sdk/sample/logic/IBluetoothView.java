package org.amv.access.sdk.sample.logic;

import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.vehicle.VehicleState;

public interface IBluetoothView {
    /**
     * Called when initialization finished successfully.
     */
    void onInitializeFinished();

    /**
     * Called when initialization failed
     *
     * @param error the initialization error
     */
    void onInitializeFailed(AccessSdkException error);

    /**
     * You can show vehicle serial in the UI.
     *
     * @param vehicleSerial The vehicle serial
     */
    void setVehicleSerial(SerialNumber vehicleSerial);

    /**
     *  Called when the broadcaster state has changed.
     *
     * @param state the state of the broadcaster
     */
    void onStateUpdate(IBluetoothController.State state);

    /**
     * Called when vehicle has updated its status
     *
     * @param status The vehicle status
     */
    void onVehicleStatusUpdate(VehicleState status);

    /**
     * Called when a command failed and an error message should be shown.
     *
     * @param title The title message.
     * @param message The failure message.
     */
    void showAlert(String title, String message);

    /**
     * Something did not work as expected(invalid vehicle state, vehicle not supporting certain
     * capabilities etc) and Activity should be finished.
     *
     * @param title The title message.
     * @param message The error message.
     */
    void finishWithMessage(String title, String message);
}
