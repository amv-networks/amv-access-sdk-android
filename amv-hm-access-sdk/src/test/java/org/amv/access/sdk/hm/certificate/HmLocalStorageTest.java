package org.amv.access.sdk.hm.certificate;


import com.google.common.base.Optional;
import com.highmobility.utils.Base64;

import junit.framework.Assert;

import org.amv.access.sdk.hm.error.SdkNotInitializedException;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.Storage;
import org.amv.access.sdk.spi.certificate.AccessCertificate;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.certificate.impl.SimpleAccessCertificatePair;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class HmLocalStorageTest {
    private static final String RANDOM_DEVICE_CERT_BASE64 = "dG1jcwAAEjRWeJq83vAAAESD1wCHryUaZL8YrHvGDUXBxXz4wDGgVRd97h/czJx7dIL/P9OGDd9qLNMI23/tCESHUCiokUvKp0b0Khf3CFJG88KVSqR5lhPa6rhUZhYBmz76iuzMOxbj8i8znubsOeNvp2bhelLQCihdhaBDARf0LLfk4O0mUMxFtdNHKtqQ5dfE79AqxnfZ";
    private static final String RANDOM_ACCESS_CERT_BASE64 = "ASOsPQ7dcIjuyn4QnCxQgp3iKYE8hszMrvsD7Lnn9YiJMkSHsGrpychf4aF6ZwuNP+R1RKfMG2DhGl4jGL+TcdGZdWNsQHhU5s9g340t/y/nnhEBBQwoEwEFDCgHEAAfCAAAQA==";

    private HmLocalStorage sut;

    @Before
    public void setUp() {
        SecureStorage secureStorage = new SimpleMapStorage();
        Storage storage = new SimpleMapStorage();

        this.sut = new HmLocalStorage(secureStorage, storage);
        this.sut.reset().blockingFirst();
    }

    @Test
    public void itShouldFailToFindMissingDeviceCertificate() throws Exception {
        try {
            sut.findDeviceCertificate().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    public void itShouldSuccessfullyStoreDeviceCertificate() throws Exception {
        byte[] randomDeviceCertBytes = Base64.decode(RANDOM_DEVICE_CERT_BASE64);
        HmDeviceCertificate hmDeviceCertificate = new HmDeviceCertificate(new com.highmobility.crypto.DeviceCertificate(randomDeviceCertBytes));

        Boolean storeSuccess = sut.storeDeviceCertificate(hmDeviceCertificate)
                .blockingFirst();

        assertThat(storeSuccess, is(Boolean.TRUE));

        DeviceCertificate deviceCertificate1 = sut.findDeviceCertificate().blockingFirst();
        assertThat(hmDeviceCertificate.toByteArray(), is(deviceCertificate1.toByteArray()));
    }

    @Test
    public void itShouldFailToFindMissingIssuerPublicKey() throws Exception {
        try {
            sut.findIssuerPublicKey().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    public void itShouldSuccessfullyStoreIssuerPublicKey() throws Exception {
        byte[] randomIssuerPublicKey = RandomUtils.nextBytes(18);

        Boolean storeSuccess = sut.storeIssuerPublicKey(randomIssuerPublicKey)
                .blockingFirst();

        assertThat(storeSuccess, is(Boolean.TRUE));

        byte[] randomIssuerPublicKey1 = sut.findIssuerPublicKey().blockingFirst();
        assertThat(randomIssuerPublicKey, is(randomIssuerPublicKey1));
    }

    @Test
    public void itShouldFailToFindMissingKeys() throws Exception {
        try {
            sut.findKeys().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(SdkNotInitializedException.class));
        }
    }

    @Test
    public void itShouldSuccessfullyStoreKeys() throws Exception {
        KeysImpl keys = KeysImpl.builder()
                .publicKey(RandomUtils.nextBytes(18))
                .privateKey(RandomUtils.nextBytes(18))
                .build();
        Boolean storeSuccess = sut.storeKeys(keys).blockingFirst();

        assertThat(storeSuccess, is(Boolean.TRUE));

        Keys keys1 = sut.findKeys().blockingFirst();
        assertThat(keys.getPublicKeyHex(), is(keys1.getPublicKeyHex()));
        assertThat(keys.getPrivateKeyHex(), is(keys1.getPrivateKeyHex()));
    }

    @Test
    public void itShouldReturnEmptyListOnMissingAccessCertificates() throws Exception {
        List<AccessCertificatePair> accessCertificatePairs = sut.findAccessCertificates()
                .toList().blockingGet();

        assertThat(accessCertificatePairs.isEmpty(), is(true));
    }

    @Test
    public void itShouldSuccessfullyStoreAccessCertificates() throws Exception {
        UUID uuid = UUID.randomUUID();
        byte[] randomAccessCertBytes = Base64.decode(RANDOM_ACCESS_CERT_BASE64);
        HmAccessCertificate hmAccessCertificate = new HmAccessCertificate(new com.highmobility.crypto.AccessCertificate(randomAccessCertBytes));
        AccessCertificatePair accessCertificatePair = SimpleAccessCertificatePair.builder()
                .id(uuid.toString())
                .deviceAccessCertificate(hmAccessCertificate)
                .vehicleAccessCertificate(hmAccessCertificate)
                .build();

        Boolean storeSuccess = sut.storeAccessCertificates(Collections.singletonList(accessCertificatePair))
                .blockingFirst();

        assertThat(storeSuccess, is(Boolean.TRUE));

        AccessCertificatePair accessCertificatePair1 = sut.findAccessCertificates().blockingFirst();
        AccessCertificate deviceAccessCertificate = accessCertificatePair1.getDeviceAccessCertificate();
        assertThat(deviceAccessCertificate.toByteArray(), is(randomAccessCertBytes));
    }

    @Test
    public void itShouldYieldSuccessOnRemovalOfNonExistingAccessCertById() throws Exception {
        List<AccessCertificatePair> accessCertificatePairs = sut.findAccessCertificates().toList().blockingGet();
        assertThat(accessCertificatePairs.isEmpty(), is(true));

        Boolean removeSuccess = sut.removeAccessCertificateById("any").blockingFirst();
        assertThat(removeSuccess, is(Boolean.TRUE));
    }


    @Test
    public void itShouldReturnEmptyOptionalOnNonExistingAccessCertById() throws Exception {
        Optional<AccessCertificatePair> accessCertificateById = sut.findAccessCertificateById(UUID.randomUUID().toString())
                .firstOrError()
                .blockingGet();

        assertThat(accessCertificateById, is(Optional.absent()));
    }

    @Test
    public void itShouldSuccessfullyFindExistingAccessCertById() throws Exception {
        UUID uuid = UUID.randomUUID();
        byte[] randomAccessCertBytes = Base64.decode(RANDOM_ACCESS_CERT_BASE64);
        HmAccessCertificate hmAccessCertificate = new HmAccessCertificate(new com.highmobility.crypto.AccessCertificate(randomAccessCertBytes));
        AccessCertificatePair accessCertificatePair = SimpleAccessCertificatePair.builder()
                .id(uuid.toString())
                .deviceAccessCertificate(hmAccessCertificate)
                .vehicleAccessCertificate(hmAccessCertificate)
                .build();

        Boolean storeSuccess = sut.storeAccessCertificates(Collections.singletonList(accessCertificatePair))
                .blockingFirst();
        assertThat(storeSuccess, is(Boolean.TRUE));

        AccessCertificatePair accessCertificateByIdOrNull = sut.findAccessCertificateById(uuid.toString())
                .firstOrError()
                .blockingGet()
                .orNull();

        assertThat(accessCertificateByIdOrNull, is(notNullValue()));
        assertThat(accessCertificateByIdOrNull.getId(), is(uuid.toString()));
    }

    @Test
    public void itShouldSuccessfullyRemoveExistingAccessCertById() throws Exception {
        UUID uuid = UUID.randomUUID();
        byte[] randomAccessCertBytes = Base64.decode(RANDOM_ACCESS_CERT_BASE64);
        HmAccessCertificate hmAccessCertificate = new HmAccessCertificate(new com.highmobility.crypto.AccessCertificate(randomAccessCertBytes));
        AccessCertificatePair accessCertificatePair = SimpleAccessCertificatePair.builder()
                .id(uuid.toString())
                .deviceAccessCertificate(hmAccessCertificate)
                .vehicleAccessCertificate(hmAccessCertificate)
                .build();

        Boolean storeSuccess = sut.storeAccessCertificates(Collections.singletonList(accessCertificatePair))
                .blockingFirst();
        assertThat(storeSuccess, is(Boolean.TRUE));

        List<AccessCertificatePair> accessCertificatePairs = sut.findAccessCertificates().toList().blockingGet();
        assertThat(accessCertificatePairs.isEmpty(), is(false));

        Boolean removeSuccess = sut.removeAccessCertificateById(accessCertificatePair.getId()).blockingFirst();
        assertThat(removeSuccess, is(Boolean.TRUE));

        List<AccessCertificatePair> accessCertificatePairs1 = sut.findAccessCertificates().toList().blockingGet();
        assertThat(accessCertificatePairs1.isEmpty(), is(true));
    }

    @Test
    public void itShouldResetStorageSuccessfully() throws Exception {
        Boolean storeKeysSuccessful = sut.storeKeys(KeysImpl.builder()
                .publicKey(RandomUtils.nextBytes(18))
                .privateKey(RandomUtils.nextBytes(18))
                .build()).blockingFirst();
        assertThat(storeKeysSuccessful, is(Boolean.TRUE));

        HmAccessCertificate hmAccessCertificate = new HmAccessCertificate(
                new com.highmobility.crypto.AccessCertificate(Base64.decode(RANDOM_ACCESS_CERT_BASE64)));
        AccessCertificatePair accessCertificatePair = SimpleAccessCertificatePair.builder()
                .id(UUID.randomUUID().toString())
                .deviceAccessCertificate(hmAccessCertificate)
                .vehicleAccessCertificate(hmAccessCertificate)
                .build();

        Boolean storeAccessCertSuccess = sut.storeAccessCertificates(Collections.singletonList(accessCertificatePair))
                .blockingFirst();
        assertThat(storeAccessCertSuccess, is(Boolean.TRUE));


        Boolean storeIssuerPublicKeySuccess = sut.storeIssuerPublicKey(RandomUtils.nextBytes(18))
                .blockingFirst();
        assertThat(storeIssuerPublicKeySuccess, is(Boolean.TRUE));

        HmDeviceCertificate hmDeviceCertificate = new HmDeviceCertificate(
                new com.highmobility.crypto.DeviceCertificate(Base64.decode(RANDOM_DEVICE_CERT_BASE64)));
        Boolean storeDeviceCertSuccess = sut.storeDeviceCertificate(hmDeviceCertificate)
                .blockingFirst();

        assertThat(storeDeviceCertSuccess, is(Boolean.TRUE));

        Boolean resetSuccess = sut.reset().blockingFirst();
        assertThat(resetSuccess, is(Boolean.TRUE));

        assertThat(sut.findKeys()
                .map(Optional::fromNullable)
                .onErrorReturnItem(Optional.absent())
                .blockingFirst(), is(Optional.absent()));
        assertThat(sut.findIssuerPublicKey()
                .map(Optional::fromNullable)
                .onErrorReturnItem(Optional.absent())
                .blockingFirst(), is(Optional.absent()));
        assertThat(sut.findDeviceCertificate()
                .map(Optional::fromNullable)
                .onErrorReturnItem(Optional.absent())
                .blockingFirst(), is(Optional.absent()));
        assertThat(sut.findAccessCertificates()
                .toList()
                .blockingGet()
                .isEmpty(), is(true));
    }

}