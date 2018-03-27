package org.amv.access.sdk.spi.certificate;


import com.google.common.base.Optional;

import io.reactivex.Observable;

public interface CertificateManager {
    /**
     * Get the locally stored device certificate.
     *
     * @return an observable emitting the locally stored device certificate
     */
    Observable<DeviceCertificate> getDeviceCertificate();

    /**
     * Retrieve all access certificates currently stored locally on the device.
     *
     * @return an observable emitting all locally present access certificates
     */
    Observable<AccessCertificatePair> getAccessCertificates();

    /**
     * Retrieve a single access certificate by id.
     *
     * @return an observable emitting an access certificate or empty
     */
    Observable<Optional<AccessCertificatePair>> getAccessCertificateById(String id);

    /**
     * Download access certificates from remote and store them locally on the device.
     *
     * @return an observable emitting all locally present access certificates after successful execution
     */
    Observable<AccessCertificatePair> refreshAccessCertificates();

    /**
     * Revoke a given access certificate pair from the remote exchange and from the device.
     *
     * @return an observable emitting true on success
     */
    Observable<Boolean> revokeAccessCertificate(AccessCertificatePair certificatePair);
}
