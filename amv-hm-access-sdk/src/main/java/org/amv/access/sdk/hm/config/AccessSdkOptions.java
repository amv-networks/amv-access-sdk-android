package org.amv.access.sdk.hm.config;

import com.google.common.base.Optional;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.hm.secure.Codec;

public interface AccessSdkOptions {

    AccessApiContext getAccessApiContext();

    String getSharedPreferencesName();

    Optional<Codec> getSecureStorageCodec();

    Optional<UserIdentity> getUserIdentity();
}
