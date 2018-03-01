package org.amv.access.sdk.hm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.soloader.SoLoader;
import com.google.common.base.Optional;
import com.highmobility.hmkit.Manager;

import org.amv.access.sdk.hm.certificate.AmvHmRemote;
import org.amv.access.sdk.hm.certificate.HmCertificateManager;
import org.amv.access.sdk.hm.certificate.HmLocalStorage;
import org.amv.access.sdk.hm.certificate.LocalStorage;
import org.amv.access.sdk.hm.certificate.Remote;
import org.amv.access.sdk.hm.communication.HmCommandFactory;
import org.amv.access.sdk.hm.config.AccessSdkOptions;
import org.amv.access.sdk.hm.config.UserIdentity;
import org.amv.access.sdk.hm.crypto.Keys;
import org.amv.access.sdk.hm.secure.Codec;
import org.amv.access.sdk.hm.secure.ConcealCodec;
import org.amv.access.sdk.hm.secure.PlaintextCodec;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.SharedPreferencesStorage;
import org.amv.access.sdk.hm.secure.SingleCodecSecureStorage;
import org.amv.access.sdk.hm.secure.Storage;

import java.util.Arrays;

import static android.content.Context.MODE_PRIVATE;
import static com.google.common.base.Preconditions.checkNotNull;

class AmvAccessSdkConfiguration {
    private static final String TAG = "AmvAccessSdkConfig";

    private final Context context;
    private final AccessSdkOptions accessSdkOptions;

    AmvAccessSdkConfiguration(Context context, AccessSdkOptions accessSdkOptions) {
        this.context = checkNotNull(context);
        this.accessSdkOptions = checkNotNull(accessSdkOptions);
    }

    AmvAccessSdk amvAccessSdk() {
        LocalStorage localStorage = localStorage();

        return new AmvAccessSdk(context,
                accessSdkOptions,
                manager(),
                localStorage,
                certificateManager(localStorage),
                commandFactory());
    }

    private Manager manager() {
        return Manager.getInstance();
    }

    private HmCommandFactory commandFactory() {
        return new HmCommandFactory();
    }

    private HmCertificateManager certificateManager(LocalStorage localStorage) {
        return new HmCertificateManager(localStorage, remote());
    }

    private Remote remote() {
        return new AmvHmRemote(accessSdkOptions.getAccessApiContext());
    }

    private LocalStorage localStorage() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        SharedPreferencesStorage sharedPreferencesStorage = sharedPreferencesStorage(sharedPreferences);
        SecureStorage secureStorage = secureStorage(sharedPreferencesStorage);
        Storage plaintextStoreage = plaintextStorage(sharedPreferencesStorage);
        HmLocalStorage hmLocalStorage = new HmLocalStorage(secureStorage, plaintextStoreage);

        resetSharedPreferencesOnMismatchingIdentity(sharedPreferences, hmLocalStorage);

        return hmLocalStorage;
    }

    private Storage plaintextStorage(SharedPreferencesStorage sharedPreferencesStorage) {
        return new SingleCodecSecureStorage(sharedPreferencesStorage, new PlaintextCodec());
    }

    private SecureStorage secureStorage(SharedPreferencesStorage sharedPreferencesStorage) {
        Codec secureStorageCodec = accessSdkOptions.getSecureStorageCodec().or(this::concealCodec);
        return new SingleCodecSecureStorage(sharedPreferencesStorage, secureStorageCodec);
    }

    private SharedPreferencesStorage sharedPreferencesStorage(SharedPreferences sharedPreferences) {
        return new SharedPreferencesStorage(sharedPreferences);
    }

    private SharedPreferences getSharedPreferences() {
        return context.getSharedPreferences(accessSdkOptions.getSharedPreferencesName(), MODE_PRIVATE);
    }

    private ConcealCodec concealCodec() {
        SoLoader.init(context, false);
        KeyChain keyChain = new SharedPrefsBackedKeyChain(context, CryptoConfig.KEY_256);
        Crypto crypto = AndroidConceal.get().createDefaultCrypto(keyChain);

        return new ConcealCodec(crypto);
    }

    private void resetSharedPreferencesOnMismatchingIdentity(SharedPreferences sharedPreferences, LocalStorage hmLocalStorage) {
        Optional<UserIdentity> userIdentity = accessSdkOptions.getUserIdentity();
        if (userIdentity.isPresent()) {
            Keys initKeys = userIdentity.get().getKeys();

            Log.d(TAG, "Found keys used for initialization");

            Optional<Keys> deviceKeysOptional = hmLocalStorage.findKeys()
                    .map(Optional::fromNullable)
                    .onErrorReturn(e -> Optional.absent())
                    .defaultIfEmpty(Optional.absent())
                    .blockingFirst();

            if (deviceKeysOptional.isPresent()) {
                Keys deviceKeys = deviceKeysOptional.get();
                boolean matchesGivenKeys = Arrays.equals(deviceKeys.getPrivateKey(), initKeys.getPrivateKey()) &&
                        Arrays.equals(deviceKeys.getPublicKey(), initKeys.getPublicKey());
                Log.d(TAG, "Found already present keys! matchesGivenKeys=" + matchesGivenKeys);

                boolean shouldResetPreferences = !matchesGivenKeys;
                if (shouldResetPreferences) {
                    Log.d(TAG, "Resetting shared preferences of LocalStorage");
                    sharedPreferences.edit().clear().commit();
                }
            }
        }
    }

}
