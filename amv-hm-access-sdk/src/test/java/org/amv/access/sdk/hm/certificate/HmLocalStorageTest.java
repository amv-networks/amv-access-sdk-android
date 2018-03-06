package org.amv.access.sdk.hm.certificate;

import junit.framework.Assert;

import org.amv.access.sdk.hm.error.SdkNotInitializedException;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.Storage;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class HmLocalStorageTest {

    private HmLocalStorage sut;

    @Before
    public void setUp() {
        SecureStorage secureStorage = new SimpleMapStorage();
        Storage storage = new SimpleMapStorage();

        this.sut = new HmLocalStorage(secureStorage, storage);
    }

    @Test
    public void findDeviceCertificateFailure() throws Exception {
        try {
            sut.findDeviceCertificate().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    @Ignore("TODO: Add sample device certificate")
    public void storeDeviceCertificate() throws Exception {
        DeviceCertificate deviceCertificate = new DeviceCertificate() {
            @Override
            public byte[] toByteArray() {
                return RandomUtils.nextBytes(18);
            }

            @Override
            public SerialNumber getDeviceSerial() {
                throw new UnsupportedOperationException();
            }
        };
        Boolean aBoolean = sut.storeDeviceCertificate(deviceCertificate)
                .blockingFirst();

        assertThat(aBoolean, is(Boolean.TRUE));

        DeviceCertificate deviceCertificate1 = sut.findDeviceCertificate().blockingFirst();
        assertThat(deviceCertificate.toByteArray(), is(deviceCertificate1.toByteArray()));
    }

    @Test
    public void findIssuerPublicKey() throws Exception {
        try {
            sut.findIssuerPublicKey().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    public void storeIssuerPublicKey() throws Exception {
        byte[] randomIssuerPublicKey = RandomUtils.nextBytes(18);

        Boolean aBoolean = sut.storeIssuerPublicKey(randomIssuerPublicKey)
                .blockingFirst();

        assertThat(aBoolean, is(Boolean.TRUE));

        byte[] randomIssuerPublicKey1 = sut.findIssuerPublicKey().blockingFirst();
        assertThat(randomIssuerPublicKey, is(randomIssuerPublicKey1));
    }

    @Test
    public void findKeys() throws Exception {
        try {
            sut.findKeys().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    public void storeKeys() throws Exception {
        KeysImpl keys = KeysImpl.builder()
                .publicKey(RandomUtils.nextBytes(18))
                .privateKey(RandomUtils.nextBytes(18))
                .build();
        Boolean aBoolean = sut.storeKeys(keys).blockingFirst();

        assertThat(aBoolean, is(Boolean.TRUE));

        Keys keys1 = sut.findKeys().blockingFirst();
        assertThat(keys.getPublicKeyHex(), is(keys1.getPublicKeyHex()));
        assertThat(keys.getPrivateKeyHex(), is(keys1.getPrivateKeyHex()));
    }

    @Test
    public void findAccessCertificates() throws Exception {
        List<AccessCertificatePair> accessCertificatePairs = sut.findAccessCertificates()
                .toList().blockingGet();

        assertThat(accessCertificatePairs.isEmpty(), is(true));
    }

    @Test
    @Ignore("TODO: Add sample access certificate")
    public void storeAccessCertificates() throws Exception {
    }

    @Test
    @Ignore("TODO: Add sample access certificate")
    public void removeAccessCertificateById() throws Exception {
    }

}