package org.amv.access.sdk.hm.certificate;

import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.SerialNumber;

import io.reactivex.Observable;

public interface Remote {
    /**
     * Request a new device certificate from the remote exchange.
     *
     * @param keys The key pair associated with this device
     * @return an observable emitting the device certificate including the issuer key
     */
    Observable<DeviceCertificateWithIssuerKey> createDeviceCertificate(Keys keys);

    /**
     * Request an existing device certificate from the remote exchange.
     *
     * @param keys         The key pair associated with this device
     * @param deviceSerial The device serial
     * @return an observable emitting the device certificate including the issuer key
     */
    Observable<DeviceCertificateWithIssuerKey> downloadDeviceCertificate(Keys keys, SerialNumber deviceSerial);

    /**
     * Download all access certificates for this device present on the remote exchange.
     *
     * @param keys              The key pair associated with this device
     * @param deviceCertificate The device certificate associated with this device
     * @return an observable emitting all present access certificates for this device.
     */
    Observable<AccessCertificatePair> downloadAccessCertificates(Keys keys, DeviceCertificate deviceCertificate);

    /**
     * Revoke a single access certificate from the remote exchange.
     *
     * @param keys                The key pair associated with this device
     * @param deviceCertificate   The device certificate associated with this device
     * @param accessCertificateId The id of the access certificate to be revoked
     * @return an observable emitting true on success
     */
    Observable<Boolean> revokeAccessCertificate(Keys keys, DeviceCertificate deviceCertificate, String accessCertificateId);
}
