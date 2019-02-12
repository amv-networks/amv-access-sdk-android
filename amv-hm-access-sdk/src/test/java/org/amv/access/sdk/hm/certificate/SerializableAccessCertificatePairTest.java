package org.amv.access.sdk.hm.certificate;

import com.highmobility.crypto.AccessCertificate;
import com.highmobility.utils.Base64;
import com.highmobility.value.Bytes;

import org.amv.access.sdk.hm.util.Json;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.impl.SimpleAccessCertificatePair;
import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


public class SerializableAccessCertificatePairTest {
    private static final String RANDOM_CERT1 = "c3YpQgz+oKNGcgjvxvLsLSacxExcWzYPwTgMTUen3nRl88eXlTU3bA3xbVWBUwFdm5boqmMwy/dJKpm1JoLL+Koj5Kng2/RqDFcQ5K2/cd1XZBELFAwBEgsUDAIHEAAfCAAAQIsAigT04daR0ppfW/X64lT6k+V5VkoV7AqZ5SOvcNfHFlvrUAKO9N2kkg+s7qAmfEHu7XlaATR00+3DTfg0oYc=";
    private static final String RANDOM_CERT2 = "VxDkrb9x3VdkcdjXEH4Y6lsiOK5s1RlIipJwX1jv45BXmyXI1W4dx3/N3+F1DIE0F3vFltxPfbyCqXRTfULF4hR9vAlSxLe9XnN2KUIM/qCjRhELFAwBEgsUDAIHEAAfCAAAQEwhVV7JMVtHvSYWCLoN1jZXvVi+DTrMZIqSOw6l5nkkHIG6sg+q9trdMl23P6AXWLCF51RfDi7Y7OqUVUxz/x0=";


    @Test
    public void itShouldSerializeAsJsonCorrectly() throws IllegalAccessException {
        String randomId = UUID.randomUUID().toString();
        Bytes bytesRandomCert1 = new Bytes(Base64.decode(RANDOM_CERT1));
        Bytes bytesRandomCert2 = new Bytes(Base64.decode(RANDOM_CERT2));
        AccessCertificatePair accessCertificatePair = SimpleAccessCertificatePair.builder()
                .id(randomId)
                .deviceAccessCertificate(new HmAccessCertificate(new AccessCertificate(bytesRandomCert1)))
                .vehicleAccessCertificate(new HmAccessCertificate(new AccessCertificate(bytesRandomCert2)))
                .build();

        SerializableAccessCertificatePair serializableAccessCertificatePair = SerializableAccessCertificatePair.from(accessCertificatePair);

        String json = Json.toJson(serializableAccessCertificatePair);

        String expectedValue = String.format("{" +
                "\"id\":\"%s\"," +
                "\"device_access_certificate\":\"%s\"," +
                "\"vehicle_access_certificate\":\"%s\"" +
                "}", randomId, RANDOM_CERT1, RANDOM_CERT2);

        assertThat(json, is(expectedValue));

        SerializableAccessCertificatePair deserialized = Json.fromJson(json, SerializableAccessCertificatePair.class);

        String deviceAccessCertificateBase64 = Base64.encode(accessCertificatePair
                .getDeviceAccessCertificate()
                .toByteArray());
        String vehicleAccessCertificateBase64 = Base64.encode(accessCertificatePair
                .getVehicleAccessCertificate()
                .toByteArray());

        assertThat(deserialized.getId(), is(equalTo(accessCertificatePair.getId())));
        assertThat(deserialized.getDeviceAccessCertificate(), is(equalTo(deviceAccessCertificateBase64)));
        assertThat(deserialized.getVehicleAccessCertificate(), is(equalTo(vehicleAccessCertificateBase64)));
    }
}