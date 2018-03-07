package org.amv.access.sdk.hm.secure;

import com.google.common.base.Optional;

import io.reactivex.Observable;

public interface Storage {
    Observable<Optional<String>> findString(String key);

    Observable<Boolean> storeString(String key, String value);

    Observable<Optional<String>> removeString(String key);
}