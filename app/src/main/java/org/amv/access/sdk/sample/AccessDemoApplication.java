package org.amv.access.sdk.sample;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.amv.access.sdk.sample.dagger.AmvAccessSdkModule;
import org.amv.access.sdk.sample.dagger.DaggerMyApplicationComponent;

import javax.inject.Inject;

import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;

public class AccessDemoApplication extends Application implements HasActivityInjector {
    public static RefWatcher getRefWatcher(Context context) {
        AccessDemoApplication application = (AccessDemoApplication) context.getApplicationContext();
        return application.refWatcher;
    }

    private RefWatcher refWatcher;

    @Inject
    DispatchingAndroidInjector<Activity> dispatchingAndroidInjector;

    @Override
    public void onCreate() {
        super.onCreate();

        DaggerMyApplicationComponent.builder()
                .amvAccessSdkModule(new AmvAccessSdkModule(this))
                .build()
                .inject(this);

        enableLeakCanary();
        enableStrictModeIfInDebugMode();
    }

    @Override
    public DispatchingAndroidInjector<Activity> activityInjector() {
        return dispatchingAndroidInjector;
    }

    private void enableLeakCanary() {
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }

        this.refWatcher = LeakCanary.install(this);
    }

    private void enableStrictModeIfInDebugMode() {
        if (BuildConfig.DEBUG) {
            enableStrictMode();
        }
    }

    private void enableStrictMode() {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyDeath()
                .build());
    }
}
