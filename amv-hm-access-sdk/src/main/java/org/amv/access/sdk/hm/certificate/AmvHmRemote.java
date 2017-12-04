package org.amv.access.sdk.hm.certificate;

import android.util.Log;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.highmobility.crypto.Crypto;
import com.highmobility.utils.Base64;

import org.amv.access.client.java6.AccessCertClient;
import org.amv.access.client.java6.Clients;
import org.amv.access.client.java6.DeviceCertClient;
import org.amv.access.client.model.java6.CreateDeviceCertificateRequestDto;
import org.amv.access.client.model.java6.DeviceCertificateDto;
import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;

import java.net.UnknownHostException;
import java.util.concurrent.Executors;

import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AmvHmRemote implements Remote {
    private static final String TAG = "AmvHmRemote";
    private static final Scheduler SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-remote-%d")
                    .build()));

    private final AccessApiContext accessApiContext;

    public AmvHmRemote(AccessApiContext accessApiContext) {
        this.accessApiContext = checkNotNull(accessApiContext);
    }

    @Override
    public Observable<DeviceCertificateWithIssuerKey> createDeviceCertificate(Keys keys) {
        checkNotNull(keys);

        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "createDeviceCertificate"))
                .flatMap(foo -> {
                    DeviceCertClient deviceCertClient = Clients.simpleDeviceCertClient(accessApiContext.getBaseUrl());

                    CreateDeviceCertificateRequestDto body = new CreateDeviceCertificateRequestDto();
                    body.device_public_key = Base64.encode(keys.getPublicKey());

                    return deviceCertClient.createDeviceCertificate(accessApiContext.getApiKey(), body);
                })
                .onErrorResumeNext(e -> {
                    String errorMessage = getErrorMessage(e);
                    return Observable.error(new RuntimeException(errorMessage, e));
                })
                .map(val -> val.device_certificate)
                .map(AmvDeviceCertificateWithIssuerKey::new)
                .map(val -> (DeviceCertificateWithIssuerKey) val)
                .doOnNext(foo -> Log.d(TAG, "createDeviceCertificate finished"));
    }

    @Override
    public Observable<AccessCertificatePair> downloadAccessCertificates(Keys keys, DeviceCertificate deviceCertificate) {
        checkNotNull(keys);
        checkNotNull(deviceCertificate);

        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "downloadAccessCertificates"))
                .flatMap(foo -> {
                    AccessCertClient client = Clients.simpleAccessCertClient(accessApiContext.getBaseUrl());

                    String[] nonce = createNonceAndSignature(keys);

                    return client.fetchAccessCertificates(nonce[0], nonce[1], deviceCertificate.getDeviceSerial());
                })
                .onErrorResumeNext(e -> {
                    String errorMessage = getErrorMessage(e);
                    return Observable.error(new RuntimeException(errorMessage, e));
                })
                .map(response -> response.access_certificates)
                .doOnNext(foo -> Log.d(TAG, "downloadAccessCertificates finished"))
                .flatMapIterable(val -> val)
                .map(HmAccessCertificatePairs::create);
    }

    @Override
    public Observable<Boolean> revokeAccessCertificate(Keys keys, DeviceCertificate deviceCertificate, String certificateId) {
        checkNotNull(keys);
        checkNotNull(deviceCertificate);
        checkNotNull(certificateId);

        return Observable.just(1)
                .subscribeOn(SCHEDULER)
                .doOnNext(foo -> Log.d(TAG, "revokeAccessCertificate"))
                .flatMap(foo -> {
                    AccessCertClient client = Clients.simpleAccessCertClient(accessApiContext.getBaseUrl());

                    String[] nonce = createNonceAndSignature(keys);

                    return client.revokeAccessCertificate(nonce[0], nonce[1], deviceCertificate.getDeviceSerial(), certificateId);
                })
                .onErrorResumeNext(e -> {
                    String errorMessage = getErrorMessage(e);
                    return Observable.error(new RuntimeException(errorMessage, e));
                })
                .doOnNext(foo -> Log.d(TAG, "revokeAccessCertificate finished"));
    }

    private String[] createNonceAndSignature(Keys keys) {
        byte[] nonce = Crypto.createSerialNumber();
        byte[] nonceSignature = Crypto.sign(nonce, keys.getPrivateKey());
        return new String[]{Base64.encode(nonce), Base64.encode(nonceSignature)};
    }

    private String getErrorMessage(Throwable error) {
        boolean hasCause = error.getCause() != null;
        if (!hasCause) {
            return error.getMessage();
        }
        Throwable cause = error.getCause();

        /*
        TODO: currently errors deserialization is not implemented in java6 based rest client
        boolean isAccessApiException = AccessSdkException.class.isAssignableFrom(cause.getClass());
        if (isAccessApiException) {
            AccessSdkException accessApiException = (AccessSdkException) error.getCause();
            ErrorResponseDto errorDto = accessApiException.getError();
            if (!errorDto.getErrors().isEmpty()) {
                ErrorResponseDto.ErrorInfoDto errorInfoDto = errorDto.getErrors().get(0);
                return errorInfoDto.getTitle() + ": " + errorInfoDto.getDetail();
            }
        }*/

        boolean isUnknownHostException = UnknownHostException.class.isAssignableFrom(cause.getClass());
        if (isUnknownHostException) {
            return "Cannot startConnecting to server. Please check internet connection.\n" + cause.getMessage();
        }

        return findCause(cause).getMessage();
    }

    private Throwable findCause(Throwable throwable) {
        return findCause(throwable, 10);
    }

    private Throwable findCause(Throwable throwable, int maxDepth) {
        checkArgument(maxDepth >= 0);

        Throwable t = throwable;
        int i = 0;
        while (t.getCause() != null) {
            t = t.getCause();

            if (i++ > maxDepth) {
                return t;
            }
        }
        return t;
    }

    public static class AmvDeviceCertificateWithIssuerKey implements DeviceCertificateWithIssuerKey {

        private final DeviceCertificateDto deviceCertificateDto;

        AmvDeviceCertificateWithIssuerKey(DeviceCertificateDto deviceCertificateDto) {
            this.deviceCertificateDto = checkNotNull(deviceCertificateDto);
        }

        @Override
        public DeviceCertificate getDeviceCertificate() {
            byte[] deviceCertificate = Base64.decode(deviceCertificateDto.device_certificate);

            return new HmDeviceCertificate(
                    new com.highmobility.crypto.DeviceCertificate(deviceCertificate)
            );
        }

        @Override
        public byte[] getIssuerPublicKey() {
            return Base64.decode(deviceCertificateDto.issuer_public_key);
        }
    }

}
