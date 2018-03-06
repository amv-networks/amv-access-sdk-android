package org.amv.access.sdk.hm.identity;

import android.util.Log;

import org.amv.access.sdk.hm.certificate.LocalStorage;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.identity.IdentityManager;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.identity.impl.IdentityImpl;

import io.reactivex.Observable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.reactivex.Observable.error;
import static org.amv.access.sdk.spi.error.AccessSdkException.wrap;

public class HmIdentityManager implements IdentityManager {
    private static final String TAG = "HmIdentityManager";

    private final LocalStorage localStorage;
    private final CertificateManager certificateManager;

    public HmIdentityManager(LocalStorage localStorage, CertificateManager certificateManager) {
        this.localStorage = checkNotNull(localStorage);
        this.certificateManager = checkNotNull(certificateManager);
    }

    @Override
    public Observable<Identity> findIdentity() {
        return Observable.just(1)
                .doOnNext(foo -> Log.d(TAG, "findIdentity"))
                .flatMap(foo -> findDeviceCertificate()
                        .map(DeviceCertificate::getDeviceSerial)
                        .zipWith(findKeys(), (serial, keys) -> IdentityImpl.builder()
                                .deviceSerial(serial)
                                .keys(keys)
                                .build()))
                .cast(Identity.class)
                .doOnNext(foo -> Log.d(TAG, "findIdentity finished"));
    }

    private Observable<Keys> findKeys() {
        return localStorage.findKeys()
                .switchIfEmpty(error(wrap(new IllegalStateException("Keys not found"))));
    }

    private Observable<DeviceCertificate> findDeviceCertificate() {
        return certificateManager.getDeviceCertificate()
                .switchIfEmpty(error(wrap(new IllegalStateException("Device certificate not found"))));
    }
}
