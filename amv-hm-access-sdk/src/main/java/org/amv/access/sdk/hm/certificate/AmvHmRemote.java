package org.amv.access.sdk.hm.certificate;

import android.util.Log;

import com.highmobility.crypto.Crypto;
import com.highmobility.utils.Base64;
import com.highmobility.value.Bytes;

import org.amv.access.client.android.AccessApiException;
import org.amv.access.client.android.AccessCertClient;
import org.amv.access.client.android.Clients;
import org.amv.access.client.android.DeviceCertClient;
import org.amv.access.client.android.model.CreateDeviceCertificateRequestDto;
import org.amv.access.client.android.model.DeviceCertificateDto;
import org.amv.access.client.android.model.ErrorResponseDto;
import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.AmvSdkSchedulers;
import org.amv.access.sdk.hm.error.CertificateRevokeException;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.SerialNumber;

import java.net.UnknownHostException;
import java.security.SecureRandom;

import io.reactivex.Observable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AmvHmRemote implements Remote {
    private static final String TAG = "AmvHmRemote";

    private final AccessApiContext accessApiContext;

    public AmvHmRemote(AccessApiContext accessApiContext) {
        this.accessApiContext = checkNotNull(accessApiContext);
    }

    @Override
    public Observable<DeviceCertificateWithIssuerKey> createDeviceCertificate(Keys keys) {
        checkNotNull(keys);

        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.remoteScheduler())
                .doOnNext(foo -> Log.d(TAG, "createDeviceCertificate"))
                .flatMap(foo -> {
                    DeviceCertClient deviceCertClient = Clients.simpleDeviceCertClient(accessApiContext.getBaseUrl());

                    CreateDeviceCertificateRequestDto body = new CreateDeviceCertificateRequestDto();
                    body.device_public_key = Base64.encode(keys.getPublicKey());

                    return deviceCertClient.createDeviceCertificate(
                            accessApiContext.getAppId(), accessApiContext.getApiKey(), body);
                })
                .onErrorResumeNext(e -> {
                    String errorMessage = getErrorMessage(e);
                    return Observable.error(new RuntimeException(errorMessage, e));
                })
                .map(val -> val.device_certificate)
                .map(AmvDeviceCertificateWithIssuerKey::new)
                .cast(DeviceCertificateWithIssuerKey.class)
                .doOnNext(foo -> Log.d(TAG, "createDeviceCertificate finished"));
    }

    @Override
    public Observable<DeviceCertificateWithIssuerKey> downloadDeviceCertificate(Keys keys,
                                                                                SerialNumber deviceSerial) {
        checkNotNull(deviceSerial);
        checkNotNull(keys);

        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.remoteScheduler())
                .doOnNext(foo -> Log.d(TAG, "downloadDeviceCertificate"))
                .flatMap(foo -> {
                    DeviceCertClient deviceCertClient = Clients.simpleDeviceCertClient(accessApiContext.getBaseUrl());

                    String[] nonce = createNonceAndSignature(keys);

                    return deviceCertClient.fetchDeviceCertificate(
                            nonce[0], nonce[1], deviceSerial.getSerialNumberHex());
                })
                .onErrorResumeNext(e -> {
                    String errorMessage = getErrorMessage(e);
                    return Observable.error(new RuntimeException(errorMessage, e));
                })
                .map(val -> val.device_certificate)
                .map(AmvDeviceCertificateWithIssuerKey::new)
                .cast(DeviceCertificateWithIssuerKey.class)
                .doOnNext(foo -> Log.d(TAG, "downloadDeviceCertificate finished"));
    }

    @Override
    public Observable<AccessCertificatePair> downloadAccessCertificates(Keys keys, DeviceCertificate deviceCertificate) {
        checkNotNull(keys);
        checkNotNull(deviceCertificate);

        return Observable.just(1)
                .subscribeOn(AmvSdkSchedulers.remoteScheduler())
                .doOnNext(foo -> Log.d(TAG, "downloadAccessCertificates"))
                .flatMap(foo -> {
                    AccessCertClient client = Clients.simpleAccessCertClient(accessApiContext.getBaseUrl());

                    String[] nonce = createNonceAndSignature(keys);

                    String serialNumberHex = deviceCertificate
                            .getDeviceSerial().getSerialNumberHex();
                    return client.fetchAccessCertificates(nonce[0], nonce[1], serialNumberHex, 1);
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

        return Observable.error(new CertificateRevokeException(
                new UnsupportedOperationException("Revoking certificates is not supported."))
        );
    }

    private String[] createNonceAndSignature(Keys keys) {
        byte[] nonce = generateNonce();
        byte[] nonceSignature = Crypto.sign(nonce, keys.getPrivateKey()).getByteArray();
        return new String[]{Base64.encode(nonce), Base64.encode(nonceSignature)};
    }

    private byte[] generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[64];
        random.nextBytes(nonce);
        return nonce;
    }

    private String getErrorMessage(Throwable error) {
        boolean isAccessApiException = AccessApiException.class.isAssignableFrom(error.getClass());
        if (isAccessApiException) {
            AccessApiException accessApiException = (AccessApiException) error;
            ErrorResponseDto errorDto = accessApiException.getError();
            if (errorDto.errors != null && !errorDto.errors.isEmpty()) {
                ErrorResponseDto.ErrorInfoDto errorInfoDto = errorDto.errors.get(0);
                return errorInfoDto.title + ": " + errorInfoDto.detail;
            }
        }

        Throwable cause = findCause(error);

        boolean isUnknownHostException = UnknownHostException.class.isAssignableFrom(cause.getClass());
        if (isUnknownHostException) {
            return "Cannot connect to server. Please check internet connection.\n" + cause.getMessage();
        }

        return cause.getMessage();
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
                    new com.highmobility.crypto.DeviceCertificate(new Bytes(deviceCertificate))
            );
        }

        @Override
        public byte[] getIssuerPublicKey() {
            return Base64.decode(deviceCertificateDto.issuer_public_key);
        }
    }

}
