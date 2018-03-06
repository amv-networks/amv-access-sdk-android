package org.amv.access.sdk.hm.error;

import org.amv.access.sdk.spi.error.AccessSdkException;

public class SdkNotInitializedException extends AccessSdkException {
    private static Type TYPE = new Type() {
        @Override
        public String getName() {
            return "UNINITIALIZED";
        }

        @Override
        public String getDescription() {
            return "The sdk has not been properly initialized.";
        }
    };

    public SdkNotInitializedException(Throwable cause) {
        super(TYPE, cause);
    }

    public SdkNotInitializedException(String message) {
        this(new IllegalStateException(message));
    }
}
