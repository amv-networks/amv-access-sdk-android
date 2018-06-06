package org.amv.access.sdk.sample.dagger;

import org.amv.access.sdk.sample.BroadcastActivity;
import org.amv.access.sdk.sample.CertificatesActivity;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class MyApplicationModule {
    @ContributesAndroidInjector
    abstract CertificatesActivity contributeCertificatesActivityInjector();

    @ContributesAndroidInjector
    abstract BroadcastActivity contributeBroadcastActivityInjector();
}