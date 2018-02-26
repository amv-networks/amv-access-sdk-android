package org.amv.access.sdk.hm.certificate;

import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;

import java.util.List;

import io.reactivex.Observable;

public interface LocalStorage {

    Observable<Boolean> storeDeviceCertificate(DeviceCertificate deviceCertificate);

    Observable<DeviceCertificate> findDeviceCertificate();

    Observable<Boolean> storeIssuerPublicKey(byte[] issuerPublicKey);

    Observable<byte[]> findIssuerPublicKey();

    Observable<Boolean> storeKeys(Keys keys);

    Observable<Keys> findKeys();

    Observable<Boolean> storeAccessCertificates(List<AccessCertificatePair> certificates);

    Observable<AccessCertificatePair> findAccessCertificates();

    Observable<Boolean> removeAccessCertificateById(String accessCertificateId);
}