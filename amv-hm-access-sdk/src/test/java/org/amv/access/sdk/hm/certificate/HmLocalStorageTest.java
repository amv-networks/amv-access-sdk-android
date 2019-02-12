package org.amv.access.sdk.hm.certificate;


import com.google.common.base.Optional;
import com.highmobility.utils.Base64;
import com.highmobility.value.Bytes;

import org.junit.Assert;

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
    private static final String RANDOM_DEVICE_CERT_BASE64 = "WFhYWAAAEjRWeJq83vAAAK+vdxh+UxMaxHp7V8rqGO0WUrxii1wd9ToGWVFLqztSLrgfdytAjtL89tJRIHZ33ai4D7b154ZTv/xuq+IqW/L/2DeqTlhsp/lsvHuYUp62cTptOwyj7uJ/HXEqw0+ims5z08Zi6XN+7t1Pa3EB4al+IHy7pwuUQPkT0Myr+1dhia0ZBShSQ20x";
    private static final String RANDOM_ACCESS_CERT_BASE64 = "AURFTU+XdapkXy/AslSvr3cYflMTGsR6e1fK6hjtFlK8YotcHfU6BllRS6s7Ui64H3crQI7S/PbSUSB2d92ouA+29eeGU7/8bqviKlvy/9g3qk5YbKf5EwIKDDgVAgwMOAcQAB8IAABA";

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
        Bytes randomDeviceCertBytes = new Bytes(Base64.decode(RANDOM_DEVICE_CERT_BASE64));
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
        Bytes randomAccessCertBytes = new Bytes(Base64.decode(RANDOM_ACCESS_CERT_BASE64));
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
        assertThat(deviceAccessCertificate.toByteArray(), is(randomAccessCertBytes.getByteArray()));
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
        Bytes randomAccessCertBytes = new Bytes(Base64.decode(RANDOM_ACCESS_CERT_BASE64));
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
        Bytes randomAccessCertBytes = new Bytes(Base64.decode(RANDOM_ACCESS_CERT_BASE64));
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

        Bytes bytesAccessCert = new Bytes(Base64.decode(RANDOM_ACCESS_CERT_BASE64));
        HmAccessCertificate hmAccessCertificate = new HmAccessCertificate(
                new com.highmobility.crypto.AccessCertificate(bytesAccessCert));
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

        Bytes bytesDeviceCert = new Bytes(Base64.decode(RANDOM_DEVICE_CERT_BASE64));
        HmDeviceCertificate hmDeviceCertificate = new HmDeviceCertificate(
                new com.highmobility.crypto.DeviceCertificate(bytesDeviceCert));
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