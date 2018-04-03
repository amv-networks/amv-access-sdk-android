package org.amv.access.sdk.hm;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.Executors;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public final class AmvSdkSchedulers {

    private static final Scheduler DEFAULT_SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-default-%d")
                    .build()));

    private static final Scheduler REMOTE_SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-remote-%d")
                    .build()));

    private static final Scheduler STORAGE_SCHEDULER = Schedulers.from(Executors
            .newFixedThreadPool(1, new ThreadFactoryBuilder()
                    .setNameFormat("amv-access-sdk-storage-%d")
                    .build()));

    public static Scheduler defaultScheduler() {
        return DEFAULT_SCHEDULER;
    }

    public static Scheduler remoteScheduler() {
        return REMOTE_SCHEDULER;
    }

    public static Scheduler storageScheduler() {
        return STORAGE_SCHEDULER;
    }

    private AmvSdkSchedulers() {
        throw new UnsupportedOperationException();
    }
}
