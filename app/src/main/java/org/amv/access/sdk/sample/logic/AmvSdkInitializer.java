package org.amv.access.sdk.sample.logic;


import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.AmvAccessSdk;
import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.config.AccessSdkOptionsImpl;
import org.amv.access.sdk.sample.util.PropertiesReader;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.crypto.impl.KeysImpl;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.identity.impl.IdentityImpl;
import org.amv.access.sdk.spi.identity.impl.SerialNumberImpl;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.NotThreadSafe;

import io.reactivex.Observable;

public final class AmvSdkInitializer {
    private static final String APPLICATION_PROPERTIES_FILE_NAME = "application.properties";

    private static final String API_BASE_URL_PROPERTY_NAME = "amv.access.api.baseUrl";
    private static final String API_KEY_PROPERTY_NAME = "amv.access.api.apiKey";
    private static final String API_APP_ID_PROPERTY_NAME = "amv.access.api.appId";

    private static final String IDENTITY_DEVICE_SERIAL_PROPERTY_NAME = "amv.access.identity.deviceSerialNumber";
    private static final String IDENTITY_PUBLIC_KEY_PROPERTY_NAME = "amv.access.identity.publicKey";
    private static final String IDENTITY_PRIVATE_KEY_PROPERTY_NAME = "amv.access.identity.privateKey";

    @VisibleForTesting
    public static Observable<AccessSdk> create(Context context) {
        return create(context, AccessSdkOptionsImpl.builder()
                .accessApiContext(createAccessApiContext(context))
                .identity(createIdentity(context).orNull())
                .build());
    }

    @VisibleForTesting
    public static Observable<AccessSdk> create(Context context, AccessSdkOptions accessSdkOptions) {
        try {
            AccessSdk accessSdk = AmvAccessSdk.create(context, accessSdkOptions);
            return accessSdk.initialize();
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    /**
     * Reads api credentials from a properties file.
     * Note that this is a security risk and should not be done in an
     * application used in production. The user of the sdk is responsible
     * for securely storing api credentials.
     * <p>
     * As general note: An attacker can only use these credentials to
     * get a device certificate associated to your application.
     * He is not able to create access certificates or gain access to
     * registered vehicles.
     *
     * @param context the application context
     * @return an access api context with values read from a properties file
     */
    public static AccessApiContext createAccessApiContext(Context context) {
        PropertiesReader propertiesReader = new PropertiesReader(context);
        Properties applicationProperties = propertiesReader.getProperties(APPLICATION_PROPERTIES_FILE_NAME);

        String apiBaseUrl = applicationProperties.getProperty(API_BASE_URL_PROPERTY_NAME);
        String apiKey = applicationProperties.getProperty(API_KEY_PROPERTY_NAME);
        String appId = applicationProperties.getProperty(API_APP_ID_PROPERTY_NAME);

        return AccessApiContext.builder()
                .baseUrl(apiBaseUrl)
                .apiKey(apiKey)
                .appId(appId)
                .build();
    }

    /**
     * Reads identity (serial + key pair) from a properties file.
     * Note that this is a security risk and should not be done in an
     * application used in production. The user of the sdk is responsible
     * for securely storing api credentials.
     *
     * @param context the application context
     * @return an identity object
     */
    public static Optional<Identity> createIdentity(Context context) {
        PropertiesReader propertiesReader = new PropertiesReader(context);
        Properties applicationProperties = propertiesReader.getProperties(APPLICATION_PROPERTIES_FILE_NAME);

        String deviceSerialNumber = applicationProperties.getProperty(IDENTITY_DEVICE_SERIAL_PROPERTY_NAME);
        String publicKey = applicationProperties.getProperty(IDENTITY_PUBLIC_KEY_PROPERTY_NAME);
        String privateKey = applicationProperties.getProperty(IDENTITY_PRIVATE_KEY_PROPERTY_NAME);

        boolean allPropertiesPresent = !(
                Strings.isNullOrEmpty(deviceSerialNumber) ||
                        Strings.isNullOrEmpty(publicKey) ||
                        Strings.isNullOrEmpty(privateKey)
        );

        if (!allPropertiesPresent) {
            return Optional.absent();
        }

        Identity identity = IdentityImpl.builder()
                .deviceSerial(SerialNumberImpl.builder()
                        .serialNumber(BaseEncoding.base16().lowerCase().decode(deviceSerialNumber))
                        .build())
                .keys(KeysImpl.builder()
                        .publicKey(BaseEncoding.base16().lowerCase().decode(publicKey))
                        .privateKey(BaseEncoding.base16().lowerCase().decode(privateKey))
                        .build())
                .build();

        return Optional.of(identity);
    }
}
