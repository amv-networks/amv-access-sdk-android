package org.amv.access.sdk.sample.dagger;

import org.amv.access.sdk.sample.logic.BluetoothController;
import org.amv.access.sdk.sample.logic.CertificatesController;
import org.amv.access.sdk.sample.logic.IBluetoothController;
import org.amv.access.sdk.sample.logic.ICertificatesController;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.certificate.CertificateManager;

import dagger.Module;
import dagger.Provides;

@Module
public class DemoApplicationModule {

    @Provides
    public IBluetoothController bluetoothController(AccessSdk accessSdk) {
        return new BluetoothController(accessSdk);
    }

    @Provides
    public ICertificatesController certificatesController(CertificateManager certificateManager) {
        return new CertificatesController(certificateManager);
    }

}