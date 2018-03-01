package org.amv.access.sdk.hm.config;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.secure.Codec;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AccessSdkOptionsImpl implements AccessSdkOptions {
    private static final String DEFAULT_SHARED_PREFS_NAME = "HM_SHARED_PREFS_ALIAS";

    @NonNull
    private AccessApiContext accessApiContext;

    @Builder.Default
    private String sharedPreferencesName = DEFAULT_SHARED_PREFS_NAME;

    private Codec secureStorageCodec;

    private UserIdentity userIdentity;

    @Override
    public Optional<Codec> getSecureStorageCodec() {
        return Optional.fromNullable(secureStorageCodec);
    }

    @Override
    public Optional<UserIdentity> getUserIdentity() {
        return Optional.fromNullable(userIdentity);
    }
}
