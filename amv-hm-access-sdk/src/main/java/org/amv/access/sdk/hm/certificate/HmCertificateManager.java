package org.amv.access.sdk.hm.certificate;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.hm.error.CertificateDownloadException;
import org.amv.access.sdk.hm.error.CertificateRevokeException;
import org.amv.access.sdk.hm.error.InvalidCertificateException;
import org.amv.access.sdk.hm.error.SdkNotInitializedException;
import org.amv.access.sdk.spi.certificate.AccessCertificate;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;

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

    public Observable<Boolean> initialize(Context context) {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "initialize"))
                .flatMap(foo -> localStorage.findKeys())
                .flatMap(this::findLocallyOrDownloadDeviceCertificateWithIssuerKey)
                .map(foo -> true)
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
                .onErrorResumeNext(e -> {
                    return Observable.error(new SdkNotInitializedException(e));
                })
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
                .onErrorResumeNext(e -> {
                    return Observable.error(new SdkNotInitializedException(e));
                })
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

    private Observable<DeviceCertificateWithIssuerKey> findLocallyOrDownloadDeviceCertificateWithIssuerKey(Keys keys) {
        return findDeviceCertificateWithIssuerKeyLocally()
                // if device cert does not exist, download and store it
                .onErrorResumeNext(downloadDeviceCertificateWithIssuerKey(keys));
    }

    private Observable<DeviceCertificateWithIssuerKey> findDeviceCertificateWithIssuerKeyLocally() {
        return localStorage.findDeviceCertificate()
                .zipWith(localStorage.findIssuerPublicKey(), Pair::create)
                .map(deviceCertificateAndKey -> new HmDeviceCertificateWithIssuerKey(
                        deviceCertificateAndKey.first,
                        deviceCertificateAndKey.second))
                .map(val -> (DeviceCertificateWithIssuerKey) val);
    }

    private Observable<DeviceCertificateWithIssuerKey> downloadDeviceCertificateWithIssuerKey(Keys keys) {
        return remote.createDeviceCertificate(keys)
                .flatMap(dc -> localStorage.storeDeviceCertificate(dc.getDeviceCertificate())
                        .map(foo -> dc))
                .flatMap(dc -> localStorage.storeIssuerPublicKey(dc.getIssuerPublicKey())
                        .map(foo -> dc));
    }
}
