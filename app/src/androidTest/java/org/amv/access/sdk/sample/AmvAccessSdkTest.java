package org.amv.access.sdk.sample;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.AmvAccessSdk;
import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.config.AccessSdkOptionsImpl;
import org.amv.access.sdk.sample.logic.AmvSdkInitializer;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.vehicle.VehicleState;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING) // run the cert delete last
@RunWith(AndroidJUnit4.class)
public class AmvAccessSdkTest {

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();
        assertEquals("org.amv.access.sdk.sample", appContext.getPackageName());
    }

    @Test
    public void initAmvAccessSdk() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        AccessSdkOptions accessSdkOptions = AccessSdkOptionsImpl.builder()
                .accessApiContext(AmvSdkInitializer.createAccessApiContext(appContext))
                .build();

        AccessSdk accessSdk = AmvAccessSdk.create(appContext, accessSdkOptions)
                .initialize()
                .blockingFirst();

        assertThat(accessSdk, is(notNullValue()));
    }

    @Test
    public void initAmvAccessSdkWithExistingKeys() throws Exception {
        // init without keys before initializing with given ones
        Identity sampleIdentity = getOrCreateUserIdentity();

        Context appContext = InstrumentationRegistry.getTargetContext();

        AccessApiContext accessApiContext = AmvSdkInitializer.createAccessApiContext(appContext);

        AccessSdkOptions accessSdkOptions = AccessSdkOptionsImpl.builder()
                .accessApiContext(accessApiContext)
                .identity(sampleIdentity)
                .build();

        AccessSdk accessSdk = AmvAccessSdk.create(appContext, accessSdkOptions)
                .initialize()
                .blockingFirst();

        assertThat(accessSdk, is(notNullValue()));

        Identity identity = accessSdk.identityManager()
                .findIdentity()
                .blockingFirst();

        assertThat(identity.getDeviceSerial().getSerialNumberHex(),
                is(sampleIdentity.getDeviceSerial().getSerialNumberHex()));
        assertThat(identity.getKeys().getPrivateKeyHex(),
                is(sampleIdentity.getKeys().getPrivateKeyHex()));
        assertThat(identity.getKeys().getPublicKeyHex(),
                is(sampleIdentity.getKeys().getPublicKeyHex()));
    }

    @Test
    public void findDeviceSerialNumber() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        SerialNumber deviceSerial = AmvSdkInitializer.create(appContext)
                .map(AccessSdk::certificateManager)
                .flatMap(CertificateManager::getDeviceCertificate)
                .map(DeviceCertificate::getDeviceSerial)
                .blockingFirst();

        assertThat(deviceSerial, is(notNullValue()));
    }

    @Test
    public void refreshAccessCertificates() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();

        List<AccessCertificatePair> accessCertificatePairs = AmvSdkInitializer.create(appContext)
                .map(AccessSdk::certificateManager)
                .flatMap(CertificateManager::refreshAccessCertificates)
                .toList()
                .blockingGet();

        assertThat(accessCertificatePairs, is(notNullValue()));
    }

    @Test
    @Ignore("Cannot mock bluetooth communication mechanism yet")
    public void connectAndUnlockVehicle() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Context appContext = InstrumentationRegistry.getTargetContext();

        Log.d("", "Create AccessSdk instance");
        AccessSdk accessSdk = AmvSdkInitializer.create(appContext)
                .flatMap(AccessSdk::initialize)
                .blockingFirst();

        Log.d("", "Create CommandFactory");
        CommandFactory commandFactory = accessSdk.commandFactory();

        Log.d("", "Create BluetoothCommunicationManager");
        BluetoothCommunicationManager communicationManager = accessSdk
                .bluetoothCommunicationManagerFactory()
                .createCommunicationManager();

        Log.d("", "Start observing incoming vehicle updates for door lock state");
        communicationManager.observeVehicleState()
                // filter commands that include DoorLockState
                .map(VehicleState::getDoorLockState)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .subscribe(doorLocksState -> {
                    Log.d("", "Got door locks state: locked=" + doorLocksState.isLocked());

                    if (doorLocksState.isLocked()) {
                        Log.d("", "Send unlock doors command");
                        communicationManager.sendCommand(commandFactory.unlockDoors());
                    } else {
                        communicationManager.terminate()
                                .doOnNext(success -> Log.d("", "terminate() yielded: " + success))
                                .doOnError(e -> Log.e("", "Error while terminate()", e))
                                .doOnComplete(() -> {
                                    Log.i("", "terminate() completed");
                                    countDownLatch.countDown();
                                })
                                .subscribe();
                    }
                });

        Log.d("", "Start observing bluetooth connection state");
        communicationManager.observeConnectionState()
                .map(ConnectionStateChangeEvent::getCurrentState)
                .subscribe(currentState -> {
                    if (currentState.isAuthenticated()) {
                        Log.d("", "Connection has been authenticated");
                        Log.d("", "Asking for vehicle state to be transmitted");
                        communicationManager.sendCommand(commandFactory.sendVehicleStatus());
                    } else if (currentState.isConnected()) {
                        Log.d("", "Connection has been established");
                    } else if (currentState.isDisconnected()) {
                        Log.d("", "Connection has been lost");
                    }
                });

        AccessCertificatePair accessCertificatePair = accessSdk.certificateManager().getAccessCertificates()
                .filter(certPair -> certPair.getDeviceAccessCertificate().isValidNow())
                .filter(certPair -> certPair.getVehicleAccessCertificate().isValidNow())
                .firstOrError()
                .blockingGet();

        communicationManager.startConnecting(accessCertificatePair)
                .subscribe(success -> Log.i("", "Started connecting"));

        countDownLatch.await();
    }

    private Identity getOrCreateUserIdentity() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        AccessSdkOptions accessSdkOptions = AccessSdkOptionsImpl.builder()
                .accessApiContext(AmvSdkInitializer.createAccessApiContext(appContext))
                .build();
        AccessSdk accessSdk = AmvAccessSdk.create(appContext, accessSdkOptions)
                .initialize()
                .blockingFirst();

        return accessSdk.identityManager().findIdentity().blockingFirst();
    }
}
