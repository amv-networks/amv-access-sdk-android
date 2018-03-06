package org.amv.access.sdk.hm.certificate;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

import org.amv.access.sdk.hm.secure.SecureStorage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

import io.reactivex.Observable;

public class SimpleMapStorage implements SecureStorage {

    private Map<String, String> map = Maps.newConcurrentMap();

    @Override
    public Observable<Optional<String>> findString(String key) {
        return Observable.just(Optional.fromNullable(map.get(key)));
    }

    @Override
    public Observable<Boolean> storeString(String key, String value) {
        return Observable.just(Pair.of(key, value))
                .doOnNext(keyValPair -> map.put(keyValPair.getKey(), keyValPair.getValue()))
                .map(foo -> true);
    }
}
