package org.amv.access.sdk.spi.identity;

import org.amv.access.sdk.spi.crypto.Keys;

public interface Identity {

    SerialNumber getDeviceSerial();

    Keys getKeys();
}
