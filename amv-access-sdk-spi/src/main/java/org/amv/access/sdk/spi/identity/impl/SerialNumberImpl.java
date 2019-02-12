package org.amv.access.sdk.spi.identity.impl;

import com.google.common.io.BaseEncoding;

import org.amv.access.sdk.spi.identity.SerialNumber;

import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

public class SerialNumberImpl implements SerialNumber {
    private final byte[] serialNumber;

    @lombok.Builder
    public SerialNumberImpl(byte[] serialNumber) {
        checkNotNull(serialNumber);

        this.serialNumber = Arrays.copyOf(serialNumber, serialNumber.length);
    }

    @Override
    public byte[] getSerialNumber() {
        return Arrays.copyOf(serialNumber, serialNumber.length);
    }

    @Override
    public String getSerialNumberHex() {
        return BaseEncoding.base16().lowerCase().encode(serialNumber);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerialNumberImpl that = (SerialNumberImpl) o;
        return Arrays.equals(serialNumber, that.serialNumber);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serialNumber);
    }
}
