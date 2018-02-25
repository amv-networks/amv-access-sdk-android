package org.amv.access.sdk.sample.logic;


import android.content.Context;

import org.amv.access.sdk.hm.AccessApiContext;
import org.amv.access.sdk.hm.AmvAccessSdk;
import org.amv.access.sdk.sample.util.PropertiesReader;
import org.amv.access.sdk.spi.AccessSdk;
import org.amv.access.sdk.spi.error.AccessSdkException;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.NotThreadSafe;

import io.reactivex.Observable;

@NotThreadSafe
public final class AmvSdkInitializer {
    private static final AtomicReference<AccessSdk> INSTANCE = new AtomicReference<>();

    private static final String APPLICATION_PROPERTIES_FILE_NAME = "application.properties";
    private static final String API_BASE_URL_PROPERTY_NAME = "amv.access.api.baseUrl";
    private static final String API_KEY_PROPERTY_NAME = "amv.access.api.apiKey";
    private static final String API_APP_ID_PROPERTY_NAME = "amv.access.api.appId";

    public static synchronized Observable<AccessSdk> create(Context context) {
        if (INSTANCE.get() != null) {
            return Observable.just(INSTANCE.get());
        }

        try {
            AccessApiContext accessApiContext = createAccessApiContext(context);
            AccessSdk accessSdk = AmvAccessSdk.create(context, accessApiContext);

            INSTANCE.set(accessSdk);

            return accessSdk
                    .initialize()
                    .map(foo -> accessSdk);
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
    private static AccessApiContext createAccessApiContext(Context context) {
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
}
