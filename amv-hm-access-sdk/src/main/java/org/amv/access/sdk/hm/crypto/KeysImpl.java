package org.amv.access.sdk.hm.crypto;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

public class KeysImpl implements Keys {
    private final byte[] publicKey;
    private final byte[] privateKey;

    public KeysImpl(byte[] publicKey, byte[] privateKey) {
        checkNotNull(publicKey);
        checkNotNull(privateKey);

        this.publicKey = Arrays.copyOf(publicKey, publicKey.length);
        this.privateKey = Arrays.copyOf(privateKey, privateKey.length);
    }

    @Override
    public byte[] getPublicKey() {
        return Arrays.copyOf(publicKey, publicKey.length);
    }

    @Override
    public byte[] getPrivateKey() {
        return Arrays.copyOf(privateKey, privateKey.length);
    }
}
