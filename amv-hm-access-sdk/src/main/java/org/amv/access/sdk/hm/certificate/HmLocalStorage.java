package org.amv.access.sdk.hm.certificate;

import android.util.Log;

import com.google.common.base.Optional;
import com.google.gson.reflect.TypeToken;
import com.highmobility.utils.Base64;
import com.highmobility.value.Bytes;

import org.amv.access.sdk.hm.AmvSdkSchedulers;
import org.amv.access.sdk.hm.error.SdkNotInitializedException;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.Storage;
import org.amv.access.sdk.hm.util.Json;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;

import java.lang.reflect.Type;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmLocalStorage implements LocalStorage {
    private static final String TAG = "HmLocalStorage";

    private static final String KEY_DEVICE_CERTIFICATE = "KEY_DEVICE_CERTIFICATE";
    private static final String KEY_ACCESS_CERTIFICATES = "KEY_ACCESS_CERTIFICATES";
    private static final String KEY_PRIVATE_KEY = "KEY_PRIVATE_KEY";
    private static final String KEY_PUBLIC_KEY = "KEY_PUBLIC_KEY";
    private static final String KEY_ISSUER_PUBLIC_KEY = "KEY_ISSUER_PUBLIC_KEY";

    private final Storage storage;
    private final SecureStorage secureStorage;

    public HmLocalStorage(SecureStorage secureStorage, Storage dataStorage) {
        this.secureStorage = checkNotNull(secureStorage);
        this.storage = checkNotNull(dataStorage);
    }

    @Override
    public Observable<DeviceCertificate> findDeviceCertificate() {
        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "findDeviceCertificate"))
                .flatMap(foo -> storage.findString(KEY_DEVICE_CERTIFICATE))
                .flatMap(deviceCertOptional -> deviceCertOptional
                        .transform(Base64::decode)
                        .transform(Bytes::new)
                        .transform(com.highmobility.crypto.DeviceCertificate::new)
                        .transform(HmDeviceCertificate::new)
                        .transform(Observable::just)
                        .or(() -> Observable.error(new SdkNotInitializedException("No device certificate found in local storage"))))
                .cast(DeviceCertificate.class)
                .doOnNext(deviceCertificate -> Log.d(TAG, "findDeviceCertificate finished: " + deviceCertificate.getDeviceSerial()));
    }

    @Override
    public Observable<Boolean> storeDeviceCertificate(org.amv.access.sdk.spi.certificate.DeviceCertificate deviceCertificate) {
        checkNotNull(deviceCertificate);

        return Observable.just(deviceCertificate)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "storeDeviceCertificate"))
                .map(val -> Base64.encode(val.toByteArray()))
                .flatMap(deviceCertificateBase64 -> storage
                        .storeString(KEY_DEVICE_CERTIFICATE, deviceCertificateBase64))
                .doOnNext(foo -> Log.d(TAG, "storeDeviceCertificate finished"));
    }

    @Override
    public Observable<byte[]> findIssuerPublicKey() {
        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "findIssuerPublicKey"))
                .flatMap(foo -> storage.findString(KEY_ISSUER_PUBLIC_KEY))
                .flatMap(issuerKeyOptional -> issuerKeyOptional
                        .transform(Base64::decode)
                        .transform(Observable::just)
                        .or(() -> Observable.error(new SdkNotInitializedException("No issuer key found in local storage"))))
                .doOnNext(foo -> Log.d(TAG, "findIssuerPublicKey finished"));
    }

    @Override
    public Observable<Boolean> storeIssuerPublicKey(byte[] issuerPublicKey) {
        checkNotNull(issuerPublicKey);

        return Observable.just(issuerPublicKey)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "storeIssuerPublicKey"))
                .map(Base64::encode)
                .flatMap(issuerKeyBase64 -> storage
                        .storeString(KEY_ISSUER_PUBLIC_KEY, issuerKeyBase64))
                .doOnNext(foo -> Log.d(TAG, "storeIssuerPublicKey finished"));
    }

    @Override
    public Observable<Keys> findKeys() {
        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "findKeys"))
                .flatMap(foo -> {
                    Single<byte[]> getPrivateKeyOrThrow = secureStorage.findString(KEY_PRIVATE_KEY)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(Base64::decode)
                            .singleOrError();

                    Single<byte[]> getPublicKeyOrThrow = secureStorage.findString(KEY_PUBLIC_KEY)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(Base64::decode)
                            .singleOrError();

                    BiFunction<byte[], byte[], KeysImpl> keysZipper = (privateKey, publicKey) ->
                            KeysImpl.builder()
                                    .publicKey(publicKey)
                                    .privateKey(privateKey)
                                    .build();
                    return getPrivateKeyOrThrow.zipWith(getPublicKeyOrThrow, keysZipper)
                            .toObservable();
                })
                .cast(Keys.class)
                .onErrorResumeNext(e -> {
                    return Observable.error(new SdkNotInitializedException(e));
                })
                .doOnNext(foo -> Log.d(TAG, "findKeys finished"));
    }

    @Override
    public Observable<Boolean> storeKeys(Keys keys) {
        checkNotNull(keys);

        return Observable.just(keys)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "storeKeys"))
                .flatMap(k -> {
                    Observable<Boolean> storePrivateKey = Observable.just(k.getPrivateKey())
                            .map(Base64::encode)
                            .flatMap(privateKeyBase64 -> secureStorage.storeString(KEY_PRIVATE_KEY, privateKeyBase64));

                    Observable<Boolean> storePublicKey = Observable.just(k.getPublicKey())
                            .map(Base64::encode)
                            .flatMap(publicKeyBase64 -> secureStorage.storeString(KEY_PUBLIC_KEY, publicKeyBase64));

                    return storePrivateKey.zipWith(storePublicKey, (s1, s2) -> s1 && s2);
                })
                .doOnNext(foo -> Log.d(TAG, "storeKeys finished"));
    }

    @Override
    public Observable<AccessCertificatePair> findAccessCertificates() {
        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "findAccessCertificates"))
                .flatMap(foo -> findAccessCertificatesWithFilter(cert -> true))
                .toList()
                .doOnSuccess(foo -> Log.d(TAG, "findAccessCertificates finished"))
                .flatMapObservable(Observable::fromIterable);
    }

    @Override
    public Observable<Optional<AccessCertificatePair>> findAccessCertificateById(String accessCertificateId) {
        checkNotNull(accessCertificateId);

        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "findAccessCertificateById"))
                .flatMap(foo -> findAccessCertificatesWithFilter(cert -> accessCertificateId.equals(cert.getId())))
                .map(Optional::fromNullable)
                .defaultIfEmpty(Optional.absent())
                .doOnNext(foo -> Log.d(TAG, "findAccessCertificateById finished"));
    }

    @Override
    public Observable<Boolean> storeAccessCertificates(List<AccessCertificatePair> certificates) {
        checkNotNull(certificates);

        return Observable.fromIterable(certificates)
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .map(SerializableAccessCertificatePair::from)
                .toList()
                .doOnSuccess(foo -> Log.d(TAG, "storeAccessCertificates"))
                .map(Json::toJson)
                .flatMapObservable(val -> storage.storeString(KEY_ACCESS_CERTIFICATES, val))
                .doOnNext(foo -> Log.d(TAG, "storeAccessCertificates finished"));
    }

    @Override
    public Observable<Boolean> removeAccessCertificateById(String accessCertificateId) {
        checkNotNull(accessCertificateId);

        return findAccessCertificates()
                .subscribeOn(AmvSdkSchedulers.storageScheduler())
                .doOnNext(foo -> Log.d(TAG, "removeAccessCertificateById"))
                .filter(t -> !accessCertificateId.equals(t.getId()))
                .toList()
                .flatMapObservable(this::storeAccessCertificates)
                .doOnNext(foo -> Log.d(TAG, "removeAccessCertificateById finished"));
    }

    @Override
    public Observable<Boolean> reset() {
        return Observable.just(true)
                .doOnNext(foo -> reset(storage))
                .doOnNext(foo -> reset(secureStorage));
    }

    private Observable<AccessCertificatePair> findAccessCertificatesWithFilter(Predicate<SerializableAccessCertificatePair> filter) {
        checkNotNull(filter);

        return Observable.just(1)
                .flatMap(foo -> storage.findString(KEY_ACCESS_CERTIFICATES))
                .map(valueOptional -> valueOptional.or("[]"))
                .map(accessCertificatesJson -> {
                    Type type = new TypeToken<List<SerializableAccessCertificatePair>>() {
                    }.getType();

                    List<SerializableAccessCertificatePair> serializableAccessCerts = Json
                            .fromJson(accessCertificatesJson, type);

                    return serializableAccessCerts;
                })
                .flatMapIterable(i -> i)
                .filter(filter)
                .map(HmAccessCertificatePairs::create);
    }

    private void reset(Storage storage) {
        storage.removeString(KEY_DEVICE_CERTIFICATE).blockingFirst();
        storage.removeString(KEY_ACCESS_CERTIFICATES).blockingFirst();
        storage.removeString(KEY_PRIVATE_KEY).blockingFirst();
        storage.removeString(KEY_PUBLIC_KEY).blockingFirst();
        storage.removeString(KEY_ISSUER_PUBLIC_KEY).blockingFirst();
    }
}