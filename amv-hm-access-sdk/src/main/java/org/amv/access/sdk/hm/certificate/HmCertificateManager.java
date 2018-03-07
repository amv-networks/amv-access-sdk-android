package org.amv.access.sdk.hm.certificate;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.highmobility.crypto.Crypto;

import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.error.CertificateDownloadException;
import org.amv.access.sdk.hm.error.CertificateRevokeException;
import org.amv.access.sdk.hm.error.CreateKeysFailedException;
import org.amv.access.sdk.hm.error.InvalidCertificateException;
import org.amv.access.sdk.hm.identity.HmKeys;
import org.amv.access.sdk.spi.certificate.AccessCertificate;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.identity.SerialNumber;

import java.util.Objects;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmCertificateManager implements CertificateManager {
    private static final String TAG = "HmCertificateManager";
    private static final Scheduler SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-certificate-%d")
                    .build()));

    private final LocalStorage localStorage;
    private final Remote remote;

    public HmCertificateManager(LocalStorage localStorage, Remote remote) {
        this.localStorage = checkNotNull(localStorage);
        this.remote = checkNotNull(remote);
    }

    public Observable<CertificateManager> initialize(Context context, AccessSdkOptions accessSdkOptions) {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "initialize"))
                .flatMap(foo -> findOrCreateKeys(accessSdkOptions))
                .flatMap(keys -> findLocallyOrDownloadDeviceCertificateWithIssuerKey(accessSdkOptions, keys))
                .doOnError(e -> {
                    Log.i(TAG, "Resetting local storage because of error during init process");
                    this.localStorage.reset().blockingFirst();
                })
                .map(foo -> this)
                .cast(CertificateManager.class)
                .doOnNext(foo -> Log.d(TAG, "initialize finished"));
    }

    @Override
    public Observable<DeviceCertificate> getDeviceCertificate() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "getDeviceCertificate"))
                .flatMap(foo -> localStorage.findDeviceCertificate())
                .doOnNext(foo -> Log.d(TAG, "getDeviceCertificate finished"));
    }

    @Override
    public Observable<AccessCertificatePair> getAccessCertificates() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "getAccessCertificates"))
                .flatMap(foo -> localStorage.findAccessCertificates())
                .toList()
                .doOnSuccess(foo -> Log.d(TAG, "getAccessCertificates finished"))
                .flatMapObservable(Observable::fromIterable);
    }

    @Override
    public Observable<Boolean> revokeAccessCertificate(AccessCertificatePair accessCertificatePair) {
        checkNotNull(accessCertificatePair);

        return Observable.just(1)
                .doOnNext(foo -> Log.d(TAG, "revokeAccessCertificate"))
                .subscribeOn(SCHEDULER)
                .flatMap(foo -> localStorage.findDeviceCertificate())
                .zipWith(localStorage.findKeys(), Pair::create)
                .flatMap(pair -> {
                    DeviceCertificate deviceCertificate = pair.first;
                    AccessCertificate accessCertificate = accessCertificatePair.getDeviceAccessCertificate();
                    boolean isAccessCertForCurrentDevice = Objects.equals(accessCertificate.getProviderSerial(),
                            deviceCertificate.getDeviceSerial());
                    if (!isAccessCertForCurrentDevice) {
                        return Observable.error(new InvalidCertificateException(
                                new Exception("Bad access certificate.")
                        ));
                    }
                    return Observable.just(pair);
                })
                .flatMap(deviceSerialAndKey -> remote.revokeAccessCertificate(deviceSerialAndKey.second, deviceSerialAndKey.first, accessCertificatePair.getId()))
                .zipWith(localStorage.removeAccessCertificateById(accessCertificatePair.getId()), (first, second) -> first && second)
                .onErrorResumeNext(e -> {
                    return Observable.error(new CertificateRevokeException(e));
                })
                .doOnNext(foo -> Log.d(TAG, "revokeAccessCertificate finished"));
    }

    @Override
    public Observable<AccessCertificatePair> refreshAccessCertificates() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "refreshAccessCertificates"))
                .flatMap(foo -> localStorage.findDeviceCertificate())
                .zipWith(localStorage.findKeys(), Pair::create)
                .flatMap(val -> remote.downloadAccessCertificates(val.second, val.first))
                .onErrorResumeNext(e -> {
                    return Observable.error(new CertificateDownloadException(e));
                })
                .toList()
                .doOnSuccess(foo -> Log.d(TAG, "refreshAccessCertificates finished"))
                .flatMapObservable(accessCertificates -> localStorage
                        .storeAccessCertificates(accessCertificates)
                        .flatMap(foo -> localStorage.findAccessCertificates()));
    }

    private Observable<DeviceCertificateWithIssuerKey> findLocallyOrDownloadDeviceCertificateWithIssuerKey(
            AccessSdkOptions accessSdkOptions, Keys keys) {
        return findDeviceCertificateWithIssuerKeyLocally(accessSdkOptions)
                // if device cert does not exist, download and store it
                .onErrorResumeNext(downloadAndStoreDeviceCertificateWithIssuerKey(accessSdkOptions, keys));
    }

    private Observable<DeviceCertificateWithIssuerKey> findDeviceCertificateWithIssuerKeyLocally(
            AccessSdkOptions accessSdkOptions) {
        return localStorage.findDeviceCertificate()
                .zipWith(localStorage.findIssuerPublicKey(), Pair::create)
                .map(deviceCertificateAndKey -> new HmDeviceCertificateWithIssuerKey(
                        deviceCertificateAndKey.first,
                        deviceCertificateAndKey.second))
                .map(val -> (DeviceCertificateWithIssuerKey) val)
                .flatMap(f -> {
                    if (accessSdkOptions.getIdentity().isPresent()) {
                        String deviceSerial = accessSdkOptions.getIdentity()
                                .transform(Identity::getDeviceSerial)
                                .transform(SerialNumber::getSerialNumberHex)
                                .get();

                        boolean matchesDeviceSerial = f.getDeviceCertificate()
                                .getDeviceSerial()
                                .getSerialNumberHex()
                                .equalsIgnoreCase(deviceSerial);

                        if (!matchesDeviceSerial) {
                            return Observable.error(new RuntimeException("Stored device cert does not match given identity"));
                        }
                    }
                    return Observable.just(f);
                });
    }

    private Observable<DeviceCertificateWithIssuerKey> downloadAndStoreDeviceCertificateWithIssuerKey(
            AccessSdkOptions accessSdkOptions, Keys keys) {
        return downloadOrCreateDeviceCertificateWithIssuerKeyRemote(accessSdkOptions, keys)
                .flatMap(dc -> localStorage.storeDeviceCertificate(dc.getDeviceCertificate())
                        .map(foo -> dc))
                .flatMap(dc -> localStorage.storeIssuerPublicKey(dc.getIssuerPublicKey())
                        .map(foo -> dc));
    }

    private Observable<DeviceCertificateWithIssuerKey> downloadOrCreateDeviceCertificateWithIssuerKeyRemote(
            AccessSdkOptions accessSdkOptions, Keys keys) {

        boolean initWithIdentity = accessSdkOptions.getIdentity().isPresent();
        if (initWithIdentity) {
            SerialNumber deviceSerial = accessSdkOptions.getIdentity()
                    .transform(Identity::getDeviceSerial)
                    .get();

            return remote.downloadDeviceCertificate(keys, deviceSerial);
        } else {
            return remote.createDeviceCertificate(keys);
        }
    }

    private Observable<Keys> findOrCreateKeys(AccessSdkOptions accessSdkOptions) {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .flatMap(foo -> isKeysPresent())
                .flatMap(keysPresent -> !keysPresent ? createAndStoreKeys(accessSdkOptions) : localStorage.findKeys());
    }

    private Observable<Boolean> isKeysPresent() {
        return localStorage.findKeys()
                .map(foo -> true)
                .defaultIfEmpty(false)
                .onErrorReturnItem(false);
    }

    private Observable<Keys> createAndStoreKeys(AccessSdkOptions accessSdkOptions) {
        return Observable.just(1)
                .doOnNext(foo -> Log.d(TAG, "createKeys"))
                .map(foo -> accessSdkOptions.getIdentity()
                        .transform(Identity::getKeys)
                        .or(() -> HmKeys.create(Crypto.createKeypair())))
                .flatMap(localStorage::storeKeys)
                .doOnNext(foo -> Log.d(TAG, "createKeys finished"))
                .onErrorResumeNext(t -> {
                    return Observable.error(new CreateKeysFailedException(t));
                })
                .flatMap(foo -> localStorage.findKeys());
    }
}
