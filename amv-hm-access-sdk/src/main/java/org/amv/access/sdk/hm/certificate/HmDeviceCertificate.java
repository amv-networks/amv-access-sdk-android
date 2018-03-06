package org.amv.access.sdk.hm.certificate;

import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.identity.impl.SerialNumberImpl;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

class HmDeviceCertificate implements DeviceCertificate {

    private final com.highmobility.crypto.DeviceCertificate deviceCertificate;

    HmDeviceCertificate(com.highmobility.crypto.DeviceCertificate deviceCertificate) {
        this.deviceCertificate = checkNotNull(deviceCertificate);
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(deviceCertificate.getBytes(), deviceCertificate.getBytes().length);
    }

    @Override
    public SerialNumber getDeviceSerial() {
        return SerialNumberImpl.builder()
                .serialNumber(deviceCertificate.getSerial())
                .build();
    }
}
