package org.amv.access.sdk.hm.identity;

import com.highmobility.crypto.HMKeyPair;

import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;

import static com.google.common.base.Preconditions.checkNotNull;

public final class HmKeys {

    public static Keys create(HMKeyPair keyPair) {
        checkNotNull(keyPair);
        return new KeysImpl(keyPair.getPublicKey().getByteArray(), keyPair.getPrivateKey().getByteArray());
    }

    private HmKeys() {
        throw new UnsupportedOperationException();
    }
}
