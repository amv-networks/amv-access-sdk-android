package org.amv.access.sdk.spi.identity.impl;

import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.identity.Identity;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class IdentityImpl implements Identity {

    @NonNull
    private SerialNumber deviceSerial;

    @NonNull
    private Keys keys;
}
