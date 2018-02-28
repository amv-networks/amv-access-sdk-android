package org.amv.access.sdk.hm;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.secure.Codec;

public interface AccessSdkOptions {

    AccessApiContext getAccessApiContext();

    String getSharedPreferencesName();

    Optional<Codec> getSecureStorageCodec();
}
