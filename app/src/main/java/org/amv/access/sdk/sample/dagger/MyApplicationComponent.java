package org.amv.access.sdk.sample.dagger;

import org.amv.access.sdk.sample.AccessDemoApplication;

import javax.inject.Singleton;

import dagger.Component;
import dagger.android.AndroidInjectionModule;
import dagger.android.AndroidInjector;

@Singleton
@Component(modules = {
        AndroidInjectionModule.class,
        MyApplicationModule.class,
        DemoApplicationModule.class,
        AmvAccessSdkModule.class
})
public interface MyApplicationComponent extends AndroidInjector<AccessDemoApplication> {
}