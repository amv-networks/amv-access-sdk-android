package org.amv.access.sdk.hm;

import android.content.Context;
import android.content.SharedPreferences;

import com.facebook.android.crypto.keychain.AndroidConceal;
import com.facebook.android.crypto.keychain.SharedPrefsBackedKeyChain;
import com.facebook.crypto.Crypto;
import com.facebook.crypto.CryptoConfig;
import com.facebook.crypto.keychain.KeyChain;
import com.facebook.soloader.SoLoader;
import com.highmobility.hmkit.Manager;

import org.amv.access.sdk.hm.certificate.AmvHmRemote;
import org.amv.access.sdk.hm.certificate.HmCertificateManager;
import org.amv.access.sdk.hm.certificate.HmLocalStorage;
import org.amv.access.sdk.hm.certificate.LocalStorage;
import org.amv.access.sdk.hm.certificate.Remote;
import org.amv.access.sdk.hm.communication.HmCommandFactory;
import org.amv.access.sdk.hm.secure.Codec;
import org.amv.access.sdk.hm.secure.ConcealCodec;
import org.amv.access.sdk.hm.secure.PlaintextCodec;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.SharedPreferencesStorage;
import org.amv.access.sdk.hm.secure.SingleCodecSecureStorage;
import org.amv.access.sdk.hm.secure.Storage;

import static android.content.Context.MODE_PRIVATE;
import static com.google.common.base.Preconditions.checkNotNull;

class AmvAccessSdkConfiguration {
    private final Context context;
    private final AccessSdkOptions accessSdkOptions;

    AmvAccessSdkConfiguration(Context context, AccessSdkOptions accessSdkOptions) {
        this.context = checkNotNull(context);
        this.accessSdkOptions = checkNotNull(accessSdkOptions);
    }

    AmvAccessSdk amvAccessSdk() {
        LocalStorage localStorage = localStorage();

        return new AmvAccessSdk(context,
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
        return new HmLocalStorage(secureStorage(), plaintextStorage());
    }

    private Storage plaintextStorage() {
        SharedPreferencesStorage sharedPreferencesStorage = sharedPreferencesStorage();
        return new SingleCodecSecureStorage(sharedPreferencesStorage, new PlaintextCodec());
    }

    private SecureStorage secureStorage() {
        SharedPreferencesStorage sharedPreferencesStorage = sharedPreferencesStorage();
        Codec secureStorageCodec = accessSdkOptions.getSecureStorageCodec().or(this::concealCodec);
        return new SingleCodecSecureStorage(sharedPreferencesStorage, secureStorageCodec);
    }

    private SharedPreferencesStorage sharedPreferencesStorage() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(accessSdkOptions.getSharedPreferencesName(), MODE_PRIVATE);
        return new SharedPreferencesStorage(sharedPreferences);
    }

    private ConcealCodec concealCodec() {
        SoLoader.init(context, false);
        KeyChain keyChain = new SharedPrefsBackedKeyChain(context, CryptoConfig.KEY_256);
        Crypto crypto = AndroidConceal.get().createDefaultCrypto(keyChain);

        return new ConcealCodec(crypto);
    }
}
