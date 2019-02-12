package org.amv.access.sdk.hm.certificate;

import org.amv.access.sdk.spi.certificate.AccessCertificate;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.identity.impl.SerialNumberImpl;

import java.util.Arrays;
import java.util.Calendar;

import static com.google.common.base.Preconditions.checkNotNull;

class HmAccessCertificate implements AccessCertificate {

    private final com.highmobility.crypto.AccessCertificate delegate;

    HmAccessCertificate(com.highmobility.crypto.AccessCertificate delegate) {
        this.delegate = checkNotNull(delegate);
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(delegate.getBytes().getByteArray(),
                delegate.getBytes().getLength());
    }

    @Override
    public SerialNumber getProviderSerial() {
        return SerialNumberImpl.builder()
                .serialNumber(delegate.getProviderSerial().getByteArray())
                .build();
    }

    @Override
    public SerialNumber getGainerSerial() {
        return SerialNumberImpl.builder()
                .serialNumber(delegate.getGainerSerial().getByteArray())
                .build();
    }

    @Override
    public Calendar getStartDate() {
        return delegate.getStartDate().getCalendar();
    }

    @Override
    public Calendar getEndDate() {
        return delegate.getEndDate().getCalendar();
    }

    @Override
    public boolean isExpired() {
        return delegate.isExpired();
    }

    @Override
    public boolean isNotValidYet() {
        return delegate.isNotValidYet();
    }

}
