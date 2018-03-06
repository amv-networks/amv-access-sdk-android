package org.amv.access.sdk.spi.identity.impl;

import com.google.common.io.BaseEncoding;

import org.amv.access.sdk.spi.crypto.impl.KeysImpl;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class KeysImplTest {

    @Test
    public void itShouldBeEasyToCreate() {
        String anyPublicKey = BaseEncoding.base16().lowerCase().encode(RandomUtils.nextBytes(18));
        String anyPrivateKey = BaseEncoding.base16().lowerCase().encode(RandomUtils.nextBytes(18));
        KeysImpl keys = KeysImpl.builder()
                .publicKey(BaseEncoding.base16().lowerCase().decode(anyPublicKey))
                .privateKey(BaseEncoding.base16().lowerCase().decode(anyPrivateKey))
                .build();

        assertThat(anyPublicKey.toLowerCase(), is(keys.getPublicKeyHex()));
        assertThat(anyPrivateKey.toLowerCase(), is(keys.getPrivateKeyHex()));
    }
}