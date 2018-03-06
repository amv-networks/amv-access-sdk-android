package org.amv.access.sdk.spi.identity;

import io.reactivex.Observable;

public interface IdentityManager {

    /**
     * Provides a way to get identity information.
     * <p>
     * The returned object can be used to initialize the sdk when using the same
     * identity on multiple devices.
     *
     * @return an observable that emits the loaded identity
     */
    Observable<Identity> findIdentity();
}
