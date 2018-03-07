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
import org.amv.access.sdk.hm.identity.HmIdentityManager;
import org.amv.access.sdk.hm.secure.ConcealCodec;
import org.amv.access.sdk.hm.secure.PlaintextCodec;
import org.amv.access.sdk.hm.secure.SecureStorage;
import org.amv.access.sdk.hm.secure.SharedPreferencesStorage;
import org.amv.access.sdk.hm.secure.SingleCodecSecureStorage;
import org.amv.access.sdk.hm.secure.Storage;
import org.amv.access.sdk.spi.certificate.CertificateManager;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.crypto.Keys;
import org.amv.access.sdk.spi.identity.Identity;
import org.amv.access.sdk.spi.identity.SerialNumber;

import java.util.Arrays;

import static android.content.Context.MODE_PRIVATE;
import static com.google.common.base.Preconditions.checkNotNull;

class AmvAccessSdkConfiguration {
    private static final String TAG = "AmvAccessSdkConfig";
    private static final String DEFAULT_SHARED_PREFS_NAME = "HM_SHARED_PREFS_ALIAS";

    private final Context context;
    private final AccessSdkOptions accessSdkOptions;

    AmvAccessSdkConfiguration(Context context, AccessSdkOptions accessSdkOptions) {
        this.context = checkNotNull(context);
        this.accessSdkOptions = checkNotNull(accessSdkOptions);
    }

    AmvAccessSdk amvAccessSdk() {
        Remote remote = accessSdkOptions.getRemote().or(this::remote);
        LocalStorage localStorage = localStorage();

        HmCertificateManager certificateManager = certificateManager(localStorage, remote);
        HmIdentityManager identityManager = identityManager(localStorage, certificateManager);
        Manager manager = manager();
        HmCommandFactory commandFactory = commandFactory();

        return new AmvAccessSdk(context,
                accessSdkOptions,
                manager,
                localStorage,
                identityManager,
                certificateManager,
                commandFactory);
    }

    private Manager manager() {
        return Manager.getInstance();
    }

    private HmIdentityManager identityManager(LocalStorage localStorage, CertificateManager certificateManager) {
        return new HmIdentityManager(localStorage, certificateManager);
    }

    private HmCommandFactory commandFactory() {
        return new HmCommandFactory();
    }

    private HmCertificateManager certificateManager(LocalStorage localStorage, Remote remote) {
        return new HmCertificateManager(localStorage, remote);
    }

    private Remote remote() {
        return new AmvHmRemote(accessSdkOptions.getAccessApiContext());
    }

    private LocalStorage localStorage() {
        SharedPreferences sharedPreferences = getSharedPreferences();
        SharedPreferencesStorage sharedPreferencesStorage = sharedPreferencesStorage(sharedPreferences);
        SecureStorage secureStorage = secureStorage(sharedPreferencesStorage);
        Storage plainStorage = plainStorage(sharedPreferencesStorage);
        HmLocalStorage hmLocalStorage = new HmLocalStorage(secureStorage, plainStorage);

        resetSharedPreferencesOnMismatchingIdentity(hmLocalStorage);

        return hmLocalStorage;
    }

    private Storage plainStorage(SharedPreferencesStorage sharedPreferencesStorage) {
        return new SingleCodecSecureStorage(sharedPreferencesStorage, new PlaintextCodec());
    }

    private SecureStorage secureStorage(SharedPreferencesStorage sharedPreferencesStorage) {
        return new SingleCodecSecureStorage(sharedPreferencesStorage, this.concealCodec());
    }

    private SharedPreferencesStorage sharedPreferencesStorage(SharedPreferences sharedPreferences) {
        return new SharedPreferencesStorage(sharedPreferences);
    }

    private SharedPreferences getSharedPreferences() {
        return context.getSharedPreferences(DEFAULT_SHARED_PREFS_NAME, MODE_PRIVATE);
    }

    private ConcealCodec concealCodec() {
        SoLoader.init(context, false);
        KeyChain keyChain = new SharedPrefsBackedKeyChain(context, CryptoConfig.KEY_256);
        Crypto crypto = AndroidConceal.get().createDefaultCrypto(keyChain);

        return new ConcealCodec(crypto);
    }

    private void resetSharedPreferencesOnMismatchingIdentity(LocalStorage localStorage) {
        Optional<Identity> userIdentityOptional = accessSdkOptions.getIdentity();
        if (!userIdentityOptional.isPresent()) {
            return;
        }

        Identity identity = userIdentityOptional.get();


        boolean shouldResetLocalStorage = shouldResetLocalStorage(identity, localStorage);
        if (shouldResetLocalStorage) {
            Log.d(TAG, "Resetting shared preferences of LocalStorage");
            localStorage.reset().blockingFirst();
        }
    }

    private boolean shouldResetLocalStorage(Identity identity, LocalStorage localStorage) {

        Keys givenKeys = identity.getKeys();
        SerialNumber givenDeviceSerial = identity.getDeviceSerial();
        Log.d(TAG, "Found keys/serial used for initialization");

        boolean givenIdentityMatchesStoredIdentity = givenKeysMatchStoredKeys(givenKeys, localStorage) &&
                givenDeviceSerialMatchesStoredDeviceSerial(givenDeviceSerial, localStorage);

        boolean shouldResetLocalStorage = !givenIdentityMatchesStoredIdentity;

        return shouldResetLocalStorage;
    }

    private boolean givenDeviceSerialMatchesStoredDeviceSerial(SerialNumber givenDeviceSerial, LocalStorage localStorage) {
        Optional<SerialNumber> deviceSerialOptional = localStorage.findDeviceCertificate()
                .map(DeviceCertificate::getDeviceSerial)
                .map(Optional::fromNullable)
                .onErrorReturn(e -> Optional.absent())
                .defaultIfEmpty(Optional.absent())
                .blockingFirst();

        if (!deviceSerialOptional.isPresent()) {
            return false;
        }

        SerialNumber storedDeviceSerial = deviceSerialOptional.get();

        boolean matchesDeviceSerial = Arrays.equals(storedDeviceSerial.getSerialNumber(), givenDeviceSerial.getSerialNumber());

        return matchesDeviceSerial;
    }

    private boolean givenKeysMatchStoredKeys(Keys givenKeys, LocalStorage localStorage) {
        Optional<Keys> deviceKeysOptional = localStorage.findKeys()
                .map(Optional::fromNullable)
                .onErrorReturn(e -> Optional.absent())
                .defaultIfEmpty(Optional.absent())
                .blockingFirst();

        if (!deviceKeysOptional.isPresent()) {
            return false;
        }

        Keys deviceKeys = deviceKeysOptional.get();
        boolean matchesPrivateKey = Arrays.equals(deviceKeys.getPrivateKey(), givenKeys.getPrivateKey());
        boolean matchesPublicKey = Arrays.equals(deviceKeys.getPublicKey(), givenKeys.getPublicKey());
        boolean matchesGivenKeys = matchesPrivateKey && matchesPublicKey;
        Log.d(TAG, "Found already present keys! matchesGivenKeys=" + matchesGivenKeys);

        return matchesGivenKeys;
    }

}
