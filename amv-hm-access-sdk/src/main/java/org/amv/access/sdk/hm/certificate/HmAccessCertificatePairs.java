package org.amv.access.sdk.hm.certificate;

import com.highmobility.crypto.AccessCertificate;
import com.highmobility.utils.Base64;
import com.highmobility.value.Bytes;

import org.amv.access.client.android.model.AccessCertificateDto;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.impl.SimpleAccessCertificatePair;

final class HmAccessCertificatePairs {

    static AccessCertificatePair create(SerializableAccessCertificatePair accessCertificate) {
        HmAccessCertificate deviceCert = fromBase64OrThrow(accessCertificate.getDeviceAccessCertificate());
        HmAccessCertificate vehicleCert = fromBase64OrThrow(accessCertificate.getVehicleAccessCertificate());

        return SimpleAccessCertificatePair.builder()
                .id(accessCertificate.getId())
                .deviceAccessCertificate(deviceCert)
                .vehicleAccessCertificate(vehicleCert)
                .build();
    }

    static AccessCertificatePair create(AccessCertificateDto accessCertificateDto) {
        HmAccessCertificate deviceCert = fromBase64OrThrow(accessCertificateDto.device_access_certificate);
        HmAccessCertificate vehicleCert = fromBase64OrThrow(accessCertificateDto.vehicle_access_certificate);

        return SimpleAccessCertificatePair.builder()
                .id(accessCertificateDto.id)
                .deviceAccessCertificate(deviceCert)
                .vehicleAccessCertificate(vehicleCert)
                .build();
    }

    private static HmAccessCertificate fromBase64OrThrow(String accessCertificateBase64) {
        try {
            Bytes accessCertBytes = new Bytes(Base64.decode(accessCertificateBase64));
            return new HmAccessCertificate(new AccessCertificate(accessCertBytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HmAccessCertificatePairs() {
        throw new UnsupportedOperationException();
    }
}
