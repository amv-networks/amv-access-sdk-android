package org.amv.access.sdk.hm.certificate;

import android.util.Log;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.reflect.TypeToken;
import com.highmobility.crypto.Crypto;
import com.highmobility.crypto.DeviceCertificate;
import com.highmobility.crypto.HMKeyPair;
import com.highmobility.utils.Base64;

import org.amv.access.sdk.hm.crypto.HmKeys;
import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.hm.error.CreateKeysFailedException;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.Storage;
import org.amv.access.sdk.hm.util.Json;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmLocalStorage implements LocalStorage {
    private static final String TAG = "HmLocalStorage";
    private static final Scheduler SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-storage-%d")
                    .build()));

    private static final String KEY_DEVICE_CERTIFICATE = "KEY_DEVICE_CERTIFICATE";
    private static final String KEY_ACCESS_CERTIFICATES = "KEY_ACCESS_CERTIFICATES";
    private static final String KEY_PRIVATE_KEY = "KEY_PRIVATE_KEY";
    private static final String KEY_PUBLIC_KEY = "KEY_PUBLIC_KEY";
    private static final String KEY_ISSUER_PUBLIC_KEY = "KEY_ISSUER_PUBLIC_KEY";

    private final Storage storage;
    private final SecureStorage secureStorage;

    private final Observable<Keys> keysObservable = Observable.just(1)
            .map(foo -> (Keys) new HmKeys(getOrCreateKeyPair()))
            .cache();

    public HmLocalStorage(SecureStorage secureStorage, Storage dataStorage) {
        this.secureStorage = checkNotNull(secureStorage);
        this.storage = checkNotNull(dataStorage);
    }

    @Override
    public Observable<org.amv.access.sdk.spi.certificate.DeviceCertificate> findDeviceCertificate() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "findDeviceCertificate"))
                .flatMap(foo -> storage.findString(KEY_DEVICE_CERTIFICATE))
                .flatMap(deviceCertOptional -> deviceCertOptional
                        .transform(DeviceCertificate::new)
                        .transform(HmDeviceCertificate::new)
                        .transform(d -> (org.amv.access.sdk.spi.certificate.DeviceCertificate) d)
                        .transform(Observable::just)
                        .or(() -> Observable.error(new IllegalStateException("No device certificate found"))))
                .doOnNext(deviceCertificate -> Log.d(TAG, "findDeviceCertificate finished: " + deviceCertificate.getDeviceSerial()));
    }

    @Override
    public Observable<Boolean> storeDeviceCertificate(org.amv.access.sdk.spi.certificate.DeviceCertificate deviceCertificate) {
        return Observable.just(deviceCertificate)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "storeDeviceCertificate"))
                .map(val -> Base64.encode(val.toByteArray()))
                .flatMap(deviceCertificateBase64 -> storage
                        .storeString(KEY_DEVICE_CERTIFICATE, deviceCertificateBase64))
                .doOnNext(foo -> Log.d(TAG, "storeDeviceCertificate finished"));
    }

    @Override
    public Observable<byte[]> findIssuerPublicKey() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "findIssuerPublicKey"))
                .flatMap(foo -> storage.findString(KEY_ISSUER_PUBLIC_KEY))
                .flatMap(issuerKeyOptional -> issuerKeyOptional
                        .transform(Base64::decode)
                        .transform(val -> Arrays.copyOf(val, val.length))
                        .transform(Observable::just)
                        .or(() -> Observable.error(new IllegalStateException("No issuer key found"))))
                .doOnNext(foo -> Log.d(TAG, "findIssuerPublicKey finished"));
    }

    @Override
    public Observable<Boolean> storeIssuerPublicKey(byte[] issuerPublicKey) {
        return Observable.just(issuerPublicKey)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "storeIssuerPublicKey"))
                .map(Base64::encode)
                .flatMap(issuerKeyBase64 -> storage
                        .storeString(KEY_ISSUER_PUBLIC_KEY, issuerKeyBase64))
                .doOnNext(foo -> Log.d(TAG, "storeIssuerPublicKey finished"));
    }

    @Override
    public Observable<Keys> findKeys() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "findKeys"))
                .flatMap(foo -> keysObservable)
                .doOnNext(foo -> Log.d(TAG, "findKeys finished"));
    }

    @Override
    public Observable<AccessCertificatePair> findAccessCertificates() {
        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "findAccessCertificates"))
                .flatMap(foo -> storage.findString(KEY_ACCESS_CERTIFICATES))
                .map(valueOptional -> valueOptional.or("[]"))
                .map(accessCertificatesJson -> {
                    Type type = new TypeToken<List<SerializableAccessCertificatePair>>() {
                    }.getType();

                    List<SerializableAccessCertificatePair> serializableAccessCerts = Json
                            .fromJson(accessCertificatesJson, type);

                    return serializableAccessCerts;
                })
                .doOnNext(foo -> Log.d(TAG, "findAccessCertificates finished"))
                .flatMapIterable(i -> i)
                .map(HmAccessCertificatePairs::create);
    }

    @Override
    public Observable<Boolean> storeAccessCertificates(List<AccessCertificatePair> certificates) {
        return Observable.fromIterable(checkNotNull(certificates))
                .subscribeOn(SCHEDULER)
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
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "removeAccessCertificateById"))
                .filter(t -> !accessCertificateId.equals(t.getId()))
                .toList()
                .flatMapObservable(this::storeAccessCertificates)
                .doOnNext(foo -> Log.d(TAG, "removeAccessCertificateById finished"));
    }

    private HMKeyPair getOrCreateKeyPair() {
        createKeysIfAbsent();

        String privateKeyBase64 = secureStorage.findString(KEY_PRIVATE_KEY).blockingFirst().get();
        String publicKeyBase64 = secureStorage.findString(KEY_PUBLIC_KEY).blockingFirst().get();

        byte[] privateKey = Base64.decode(privateKeyBase64);
        byte[] publicKey = Base64.decode(publicKeyBase64);

        return new HMKeyPair(privateKey, publicKey);
    }

    private void createKeysIfAbsent() {
        if (!isKeysPresent()) {
            createKeys();
        }
    }

    private boolean isKeysPresent() {
        try {
            Optional<String> privateKey = secureStorage.findString(KEY_PRIVATE_KEY).blockingFirst();
            Optional<String> publicKey = secureStorage.findString(KEY_PUBLIC_KEY).blockingFirst();

            return privateKey.isPresent() && publicKey.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private void createKeys() {
        try {
            Log.d(TAG, "createKeys");
            HMKeyPair keyPair = Crypto.createKeypair();

            secureStorage.storeString(KEY_PRIVATE_KEY, keyPair.getPrivateKeyBase64()).blockingFirst();
            secureStorage.storeString(KEY_PUBLIC_KEY, keyPair.getPublicKeyBase64()).blockingFirst();
            Log.d(TAG, "createKeys finished");
        } catch (Exception e) {
            throw new CreateKeysFailedException(e);
        }
    }

}