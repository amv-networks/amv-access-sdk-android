package org.amv.access.sdk.sample.dagger;

import android.app.Application;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.AmvAccessSdk;
import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.config.AccessSdkOptionsImpl;
import org.amv.access.sdk.sample.logic.AmvSdkInitializer;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.identity.IdentityManager;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static java.util.Objects.requireNonNull;

@Module
public class AmvAccessSdkModule {

    private final Application application;

    public AmvAccessSdkModule(Application application) {
        this.application = requireNonNull(application);
    }

    @Provides
    public Application application() {
        return application;
    }

    @Provides
    @Singleton
    public AccessApiContext accessApiContext(Application application) {
        return AmvSdkInitializer.createAccessApiContext(application);
    }

    @Provides
    @Named("optionalIdentity")
    @Singleton
    public Optional<Identity> identity(Application application) {
        return AmvSdkInitializer.createIdentity(application);
    }

    @Provides
    @Singleton
    public AccessSdkOptions acessSdkOptions(AccessApiContext accessApiContext, @Named("optionalIdentity") Optional<Identity> identity) {
        return AccessSdkOptionsImpl.builder()
                .accessApiContext(accessApiContext)
                .identity(identity.orNull())
                .build();
    }

    @Provides
    @Singleton
    public AccessSdk accessSdk(Application application, AccessSdkOptions acessSdkOptions) {
        return AmvAccessSdk.create(application, acessSdkOptions)
                .initialize()
                .singleOrError()
                .blockingGet();
    }
    @Provides
    @Singleton
    public CertificateManager certificateManager(AccessSdk accessSdk) {
        return accessSdk.certificateManager();
    }

    @Provides
    @Singleton
    public IdentityManager identityManager(AccessSdk accessSdk) {
        return accessSdk.identityManager();
    }

    @Provides
    @Singleton
    public CommandFactory commandFactory(AccessSdk accessSdk) {
        return accessSdk.commandFactory();
    }
}
