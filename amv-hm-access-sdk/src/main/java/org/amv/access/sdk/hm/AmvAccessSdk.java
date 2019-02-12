package org.amv.access.sdk.hm;

import android.content.Context;
import android.util.Log;

import com.highmobility.crypto.value.PrivateKey;
import com.highmobility.crypto.value.PublicKey;
import com.highmobility.hmkit.HMKit;
import com.highmobility.hmkit.Manager;
import com.highmobility.value.Bytes;

import org.amv.access.sdk.hm.bluetooth.BluetoothBroadcaster;
import org.amv.access.sdk.hm.bluetooth.HmBluetoothBroadcaster;
import org.amv.access.sdk.hm.bluetooth.HmBluetoothCommunicationManager;
import org.amv.access.sdk.hm.certificate.HmCertificateManager;
import org.amv.access.sdk.hm.certificate.LocalStorage;
import org.amv.access.sdk.hm.communication.HmCommandFactory;
import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.config.AccessSdkOptionsImpl;
import org.amv.access.sdk.hm.identity.HmIdentityManager;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.bluetooth.BluetoothCommunicationManager;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.communication.CommunicationManagerFactory;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.IdentityManager;

import io.reactivex.Observable;

import static com.google.common.base.Preconditions.checkNotNull;

public class AmvAccessSdk implements AccessSdk {
    private static final String TAG = "AmvAccessSdk";

    public static AccessSdk create(Context context, AccessApiContext accessApiContext) {
        return create(context, AccessSdkOptionsImpl.builder()
                .accessApiContext(accessApiContext)
                .build());
    }

    public static AccessSdk create(Context context, AccessSdkOptions accessSdkOptions) {
        checkNotNull(context);
        checkNotNull(accessSdkOptions);

        AmvAccessSdkConfiguration config = new AmvAccessSdkConfiguration(context, accessSdkOptions);
        return config.amvAccessSdk();
    }

    private final Context context;
    private final AccessSdkOptions accessSdkOptions;
    private final HMKit manager;
    private final LocalStorage localStorage;
    private final HmIdentityManager identityManager;
    private final HmCertificateManager certificateManager;
    private final HmCommandFactory commandFactory;

    AmvAccessSdk(Context context,
                 AccessSdkOptions accessSdkOptions,
                 HMKit manager,
                 LocalStorage localStorage,
                 HmIdentityManager identityManager,
                 HmCertificateManager certificateManager,
                 HmCommandFactory commandFactory) {
        this.context = checkNotNull(context);
        this.accessSdkOptions = checkNotNull(accessSdkOptions);
        this.manager = checkNotNull(manager);
        this.identityManager = checkNotNull(identityManager);
        this.localStorage = checkNotNull(localStorage);
        this.certificateManager = checkNotNull(certificateManager);
        this.commandFactory = checkNotNull(commandFactory);
    }

    @Override
    public Observable<AccessSdk> initialize() {
        return Observable.just(1)
                .doOnNext(foo -> {
                    Log.d(TAG, "initialize");
                })
                .flatMap(foo -> certificateManager.initialize(context, accessSdkOptions))
                .flatMap(foo -> initializeHmManager())
                .map(foo -> this)
                .cast(AccessSdk.class)
                .doOnNext(foo -> Log.d(TAG, "initialize finished"));
    }

    @Override
    public IdentityManager identityManager() {
        return identityManager;
    }

    @Override
    public CertificateManager certificateManager() {
        return certificateManager;
    }

    @Override
    public CommunicationManagerFactory<BluetoothCommunicationManager> bluetoothCommunicationManagerFactory() {
        return () -> new HmBluetoothCommunicationManager(createBluetoothBroadcaster());
    }

    @Override
    public CommandFactory commandFactory() {
        return commandFactory;
    }

    private BluetoothBroadcaster createBluetoothBroadcaster() {
        return new HmBluetoothBroadcaster(commandFactory, manager.getBroadcaster());
    }

    private Observable<Boolean> initializeHmManager() {
        Observable<byte[]> issuerPublicKeyObservable = localStorage.findIssuerPublicKey();

        Observable<byte[]> devicePrivateKeyObservable = localStorage.findKeys()
                .map(Keys::getPrivateKey);

        Observable<com.highmobility.crypto.DeviceCertificate> hmDeviceCertObservable = localStorage
                .findDeviceCertificate()
                .map(DeviceCertificate::toByteArray)
                .map(Bytes::new)
                .map(com.highmobility.crypto.DeviceCertificate::new);

        return Observable.zip(
                issuerPublicKeyObservable,
                devicePrivateKeyObservable,
                hmDeviceCertObservable,
                (issuerPublicKey, devicePrivateKey, deviceCert) -> {
                    manager.initialise(
                            deviceCert,
                            new PrivateKey(new Bytes(devicePrivateKey)),
                            new PublicKey(new Bytes(issuerPublicKey)),
                            context);
                    return true;
                }
        );
    }
}
