package org.amv.access.sdk.spi.crypto;

public interface Keys {
    byte[] getPublicKey();

    byte[] getPrivateKey();

    String getPublicKeyHex();

    String getPrivateKeyHex();
}
