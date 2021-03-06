package org.amv.access.sdk.hm.bluetooth;

import android.util.Log;

import com.highmobility.autoapi.CommandResolver;
import com.highmobility.hmkit.ConnectedLink;
import com.highmobility.hmkit.ConnectedLinkListener;
import com.highmobility.hmkit.error.LinkError;
import com.highmobility.hmkit.Link;
import com.highmobility.value.Bytes;

import org.amv.access.sdk.hm.AmvSdkSchedulers;
import org.amv.access.sdk.spi.bluetooth.ConnectionState;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.IncomingCommandEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleIncomingCommandEvent;
import org.amv.access.sdk.spi.communication.Command;
import org.amv.access.sdk.spi.communication.CommandFactory;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothConnection implements BluetoothConnection {
    private static final String TAG = "HmBluetoothConnection";

    private final CommandFactory commandFactory;
    private final ConnectedLink connectedLink;
    private final PublishSubject<ConnectionStateChangeEvent> linkStateSubject;
    private final PublishSubject<IncomingCommandEvent> incomingCommandsSubject;

    HmBluetoothConnection(CommandFactory commandFactory, ConnectedLink connectedLink) {
        this.commandFactory = checkNotNull(commandFactory);
        this.connectedLink = checkNotNull(connectedLink);

        this.linkStateSubject = PublishSubject.create();
        this.incomingCommandsSubject = PublishSubject.create();

        this.connectedLink.setListener(new HmBtleConnectedLinkListener());
    }

    @Override
    public Observable<Boolean> sendCommand(Command command) {
        checkNotNull(command);

        return Observable.<Boolean>create(subscriber -> {
            Log.d(TAG, "Sending command '" + command.getType().getId() + "'");

            connectedLink.sendCommand(new Bytes(command.getBytes()), new Link.CommandCallback() {
                @Override
                public void onCommandSent() {
                    Log.d(TAG, "Command '" + command.getType().getId() + "' successfully sent");
                    if (!subscriber.isDisposed()) {
                        subscriber.onNext(true);
                        subscriber.onComplete();
                    }
                }

                @Override
                public void onCommandFailed(LinkError linkError) {
                    String errorMessage = linkError.getType() + ": " + linkError.getMessage();
                    Log.d(TAG, "Command '" + command.getType().getId() + "' failed: " + errorMessage);

                    if (!subscriber.isDisposed()) {
                        subscriber.onError(new RuntimeException(errorMessage));
                    }
                }
            });
        }).subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<Boolean> terminate() {
        return Observable.fromCallable(() -> {
            connectedLink.sendCommand(new Bytes(commandFactory.disconnect().getBytes()), new NoopCommandCallback());
            return true;
        }).subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<ConnectionStateChangeEvent> observeConnectionState() {
        return this.linkStateSubject.share().subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    @Override
    public Observable<IncomingCommandEvent> observeIncomingCommands() {
        return incomingCommandsSubject.share().subscribeOn(AmvSdkSchedulers.defaultScheduler());
    }

    private void closeStreamsIfNecessary() {
        if (!linkStateSubject.hasComplete()) {
            linkStateSubject.onComplete();
        }
        if (!incomingCommandsSubject.hasComplete()) {
            incomingCommandsSubject.onComplete();
        }
    }

    private class HmBtleConnectedLinkListener implements ConnectedLinkListener {
        @Override
        public void onAuthorizationRequested(ConnectedLink link, AuthorizationCallback authorizationCallback) {
            // hmkit will automatically authorize incoming connections.
            // `onAuthorizationRequested` is only called when the vehicle wants to register certificates via bluetooth.
            // this is not the case in our scenario (where the mobile phone handles all certificates).
            // this callback is just implemented for safety reasons but is not invoked in newer versions of hmkit (2017-11-30)!
            Log.w(TAG, "connection has not been authorized by hmkit automatically - approve authorization request manually");

            authorizationCallback.approve();
        }

        @Override
        public void onAuthorizationTimeout(ConnectedLink link) {
            Log.w(TAG, "authorization timed out");
            closeStreamsIfNecessary();
        }

        @Override
        public void onStateChanged(Link link, Link.State oldState) {
            ConnectionState newHmState = HmBluetoothStates.from(link.getState());
            ConnectionState oldHmState = HmBluetoothStates.from(oldState);

            Log.d(TAG, "connection state changed from "
                    + "'" + oldState + "' to '" + link.getState() + "'");

            linkStateSubject.onNext(SimpleConnectionStateChangeEvent.builder()
                    .currentState(newHmState)
                    .previousState(oldHmState)
                    .build());

            if (newHmState.isDisconnected()) {
                closeStreamsIfNecessary();
            }
        }

        @Override
        public void onCommandReceived(Link link, Bytes bytes) {
            try {
                com.highmobility.autoapi.Command command = CommandResolver.resolve(bytes);

                Log.d(TAG, "Command received: " + command.getType());

                incomingCommandsSubject.onNext(SimpleIncomingCommandEvent.builder()
                        .command(bytes.getByteArray())
                        .build());
            } catch (Exception e) {
                Log.e(TAG, "Unknown incoming command received.");
            }
        }

    }

    private static class NoopCommandCallback implements Link.CommandCallback {

        @Override
        public void onCommandSent() {
            Log.d("NoopCommandCallback", "onCommandSent");
        }

        @Override
        public void onCommandFailed(LinkError linkError) {
            Log.d("NoopCommandCallback", "onCommandFailed: " + linkError.getMessage());
        }
    }
}
