package org.amv.access.sdk.hm;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.secure.Codec;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AccessSdkOptionsImpl implements AccessSdkOptions {
    @NonNull
    private AccessApiContext accessApiContext;

    @Builder.Default
    private String sharedPreferencesName = "HM_SHARED_PREFS_ALIAS";

    private Codec secureStorageCodec;

    @Override
    public Optional<Codec> getSecureStorageCodec() {
        return Optional.fromNullable(secureStorageCodec);
    }
}
