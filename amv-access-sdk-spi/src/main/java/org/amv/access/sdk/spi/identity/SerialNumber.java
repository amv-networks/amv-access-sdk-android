package org.amv.access.sdk.spi.identity;

public interface SerialNumber {
    byte[] getSerialNumber();

    String getSerialNumberHex();
}
