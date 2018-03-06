package org.amv.access.sdk.spi.certificate;

import org.amv.access.sdk.spi.identity.SerialNumber;

/**
 * An interface representing a device certificate.
 */
public interface DeviceCertificate {
    byte[] toByteArray();

    SerialNumber getDeviceSerial();

    //byte[] getIssuer();
    //byte[] getAppIdentifier();
    //byte[] getPublicKey();
    //byte[] getCertificateData();
    //byte[] getSignature();
}
