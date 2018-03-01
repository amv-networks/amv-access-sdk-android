package org.amv.access.sdk.hm.config;

import org.amv.access.sdk.hm.crypto.Keys;

public interface UserIdentity {

    byte[] getDeviceSerial();

    Keys getKeys();
}
