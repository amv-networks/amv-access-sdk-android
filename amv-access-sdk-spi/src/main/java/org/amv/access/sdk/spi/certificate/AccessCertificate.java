package org.amv.access.sdk.spi.certificate;

import org.amv.access.sdk.spi.identity.SerialNumber;

import java.util.Calendar;

/**
 * An interface representing an access certificate.
 */
public interface AccessCertificate {

    byte[] toByteArray();

    SerialNumber getProviderSerial();

    SerialNumber getGainerSerial();

    Calendar getStartDate();

    Calendar getEndDate();

    boolean isExpired();

    boolean isNotValidYet();

    default boolean isValidNow() {
        return !isExpired() && !isNotValidYet();
    }
}
