package org.amv.access.sdk.hm.config;

import org.amv.access.sdk.hm.crypto.Keys;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class UserIdentityImpl implements UserIdentity {

    @NonNull
    private byte[] deviceSerial;

    @NonNull
    private Keys keys;
}
