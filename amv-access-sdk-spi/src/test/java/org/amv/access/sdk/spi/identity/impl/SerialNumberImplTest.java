package org.amv.access.sdk.spi.identity.impl;

import com.google.common.io.BaseEncoding;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SerialNumberImplTest {

    @Test
    public void itShouldBeEasyToCreate() {
        String anySerialNumber = BaseEncoding.base16().lowerCase()
                .encode(RandomUtils.nextBytes(18));
        SerialNumberImpl serialNumber = SerialNumberImpl.builder()
                .serialNumber(BaseEncoding.base16().lowerCase().decode(anySerialNumber))
                .build();

        assertThat(serialNumber.getSerialNumberHex(), is(anySerialNumber));
    }
}