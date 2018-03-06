package org.amv.access.sdk.spi;

import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.communication.CommunicationManagerFactory;
import org.amv.access.sdk.spi.identity.IdentityManager;

import io.reactivex.Observable;

public interface AccessSdk {
    /**
     * Initialization routine for the setting up the sdk.
     * <p>
     * After the returned observable completes, the sdk is ready to be used.
     *
     * @return an observable that completes on successful initialization
     */
    Observable<AccessSdk> initialize();

    /**
     * Provides a mechanism to handle and manage identities.
     *
     * @return an instance of IdentityManager
     */
    IdentityManager identityManager();

    /**
     * Provides a mechanism to handle and manage certificates.
     *
     * @return an instance of CertificateManager
     */
    CertificateManager certificateManager();

    /**
     * Provides a mechanism to create a manager for handling the lifecycle of bluetooth
     * communications with a connected device.
     *
     * @return a factory creating instances of BluetoothCommunicationManager
     */
    CommunicationManagerFactory<BluetoothCommunicationManager> bluetoothCommunicationManagerFactory();

    /**
     * Provides a factory to create commands that can be used in communications
     * with connected devices.
     *
     * @return an instance of BluetoothCommunicationManager
     */
    CommandFactory commandFactory();
}
