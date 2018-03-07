package org.amv.access.sdk.hm.secure;

import android.content.SharedPreferences;

import com.google.common.base.Optional;

import io.reactivex.Observable;

import static com.google.common.base.Preconditions.checkNotNull;

public class SharedPreferencesStorage implements Storage {

    private final SharedPreferences sharedPreferences;

    public SharedPreferencesStorage(SharedPreferences sharedPreferences) {
        this.sharedPreferences = checkNotNull(sharedPreferences);
    }

    @Override
    public Observable<Optional<String>> findString(String key) {
        return Observable.just(key)
                .map(this::findStringInternal);
    }

    @Override
    public Observable<Boolean> storeString(String key, String value) {
        checkNotNull(key);
        checkNotNull(value);

        return Observable.just(key, value)
                .buffer(2)
                .map(list -> {
                    storeStringInternal(list.get(0), list.get(1));
                    return true;
                });
    }

    @Override
    public Observable<Optional<String>> removeString(String key) {
        return findString(key)
                .doOnNext(val -> {
                    sharedPreferences.edit().remove(key).commit();
                });
    }

    private Optional<String> findStringInternal(String key) {
        checkNotNull(key);

        String valueOrNull = sharedPreferences.getString(key, null);
        return Optional.fromNullable(valueOrNull);
    }

    private void storeStringInternal(String key, String value) {
        checkNotNull(key);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);

        editor.commit();
    }
}