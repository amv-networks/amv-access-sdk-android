package org.amv.access.sdk.spi.crypto.impl;

import com.google.common.io.BaseEncoding;

import org.amv.access.sdk.spi.crypto.Keys;

import java.util.Arrays;

import lombok.Builder;

import static com.google.common.base.Preconditions.checkNotNull;

public class KeysImpl implements Keys {
    private final byte[] publicKey;
    private final byte[] privateKey;

    @Builder
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
    public String getPublicKeyHex() {
        return BaseEncoding.base16().lowerCase().encode(publicKey);
    }

    @Override
    public byte[] getPrivateKey() {
        return Arrays.copyOf(privateKey, privateKey.length);
    }

    @Override
    public String getPrivateKeyHex() {
        return BaseEncoding.base16().lowerCase().encode(privateKey);
    }
}
