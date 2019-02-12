package org.amv.access.sdk.hm.identity;

import com.google.common.io.BaseEncoding;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;

import org.amv.access.sdk.hm.certificate.LocalStorage;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;
import org.amv.access.sdk.spi.identity.impl.SerialNumberImpl;
import org.junit.Test;

import io.reactivex.Observable;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class HmIdentityManagerTest {
    @Test
    public void itShouldFailOnGettingIdentityIfDeviceCertificateIsMissing() throws Exception {
        CertificateManager certificateManagerMock = mock(CertificateManager.class);
        LocalStorage localStorageMock = mock(LocalStorage.class);

        doReturn(Observable.empty())
                .when(certificateManagerMock)
                .getDeviceCertificate();
        doReturn(Observable.empty())
                .when(localStorageMock)
                .findKeys();

        HmIdentityManager sut = new HmIdentityManager(localStorageMock, certificateManagerMock);

        try {
            sut.findIdentity().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(AccessSdkException.class));
            assertThat(e.getMessage(), is("Device certificate not found"));
        }
    }

    @Test
    public void itShouldFailOnGettingIdentityOnErrorGettingDeviceCertificate() throws Exception {
        CertificateManager certificateManagerMock = mock(CertificateManager.class);
        LocalStorage localStorageMock = mock(LocalStorage.class);

        doReturn(Observable.error(new RuntimeException("Mocked error1")))
                .when(certificateManagerMock)
                .getDeviceCertificate();
        doReturn(Observable.error(new RuntimeException("Mocked error2")))
                .when(localStorageMock)
                .findKeys();

        HmIdentityManager sut = new HmIdentityManager(localStorageMock, certificateManagerMock);

        try {
            sut.findIdentity().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage(), is("Mocked error1"));
        }
    }

    @Test
    public void itShouldFailOnGettingIdentityIfKeysAreMissing() throws Exception {
        CertificateManager certificateManagerMock = mock(CertificateManager.class);
        LocalStorage localStorageMock = mock(LocalStorage.class);

        SerialNumber serialNumber = SerialNumberImpl.builder()
                .serialNumber(RandomUtils.nextBytes(9))
                .build();
        DeviceCertificate deviceCertificateMock = mock(DeviceCertificate.class);

        doReturn(serialNumber)
                .when(deviceCertificateMock)
                .getDeviceSerial();
        doReturn(Observable.just(deviceCertificateMock))
                .when(certificateManagerMock)
                .getDeviceCertificate();

        doReturn(Observable.empty())
                .when(localStorageMock)
                .findKeys();

        HmIdentityManager sut = new HmIdentityManager(localStorageMock, certificateManagerMock);

        try {
            sut.findIdentity().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e, instanceOf(AccessSdkException.class));
            assertThat(e.getMessage(), is("Keys not found"));
        }
    }

    @Test
    public void itShouldFailOnGettingIdentityOnErrorGettingKeys() throws Exception {
        CertificateManager certificateManagerMock = mock(CertificateManager.class);
        LocalStorage localStorageMock = mock(LocalStorage.class);

        SerialNumber serialNumber = SerialNumberImpl.builder()
                .serialNumber(RandomUtils.nextBytes(9))
                .build();
        DeviceCertificate deviceCertificateMock = mock(DeviceCertificate.class);

        doReturn(serialNumber)
                .when(deviceCertificateMock)
                .getDeviceSerial();
        doReturn(Observable.just(deviceCertificateMock))
                .when(certificateManagerMock)
                .getDeviceCertificate();

        doReturn(Observable.error(new RuntimeException("Mocked error2")))
                .when(localStorageMock)
                .findKeys();

        HmIdentityManager sut = new HmIdentityManager(localStorageMock, certificateManagerMock);

        try {
            sut.findIdentity().blockingFirst();
            Assert.fail("Should have thrown exception");
        } catch (Exception e) {
            assertThat(e.getMessage(), is("Mocked error2"));
        }
    }

    @Test
    public void itShouldGetIdentitySuccessfully() throws Exception {
        CertificateManager certificateManagerMock = mock(CertificateManager.class);
        LocalStorage localStorageMock = mock(LocalStorage.class);

        SerialNumber serialNumber = SerialNumberImpl.builder()
                .serialNumber(RandomUtils.nextBytes(9))
                .build();
        DeviceCertificate deviceCertificateMock = mock(DeviceCertificate.class);

        doReturn(serialNumber)
                .when(deviceCertificateMock)
                .getDeviceSerial();
        doReturn(Observable.just(deviceCertificateMock))
                .when(certificateManagerMock)
                .getDeviceCertificate();

        Keys keys = KeysImpl.builder()
                .privateKey(BaseEncoding.base16().decode("ABCD"))
                .publicKey(BaseEncoding.base16().decode("CDEF"))
                .build();
        doReturn(Observable.just(keys))
                .when(localStorageMock)
                .findKeys();

        HmIdentityManager sut = new HmIdentityManager(localStorageMock, certificateManagerMock);

        Identity identity = sut.findIdentity().blockingFirst();
        assertThat(identity, is(notNullValue()));
        assertThat(identity.getDeviceSerial(), is(serialNumber));
        assertThat(identity.getKeys().getPrivateKey(), is(keys.getPrivateKey()));
        assertThat(identity.getKeys().getPublicKey(), is(keys.getPublicKey()));
    }
}