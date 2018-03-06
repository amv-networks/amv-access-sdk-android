package org.amv.access.sdk.hm.identity;

import com.highmobility.crypto.KeyPair;

import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;

import static com.google.common.base.Preconditions.checkNotNull;

public final class HmKeys {

    public static Keys create(KeyPair keyPair) {
        checkNotNull(keyPair);
        return new KeysImpl(keyPair.getPublicKey(), keyPair.getPrivateKey());
    }

    private HmKeys() {
        throw new UnsupportedOperationException();
    }
}
