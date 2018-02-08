package org.amv.access.sdk.hm.bluetooth;

import android.util.Log;

import com.google.common.base.Optional;
import com.highmobility.autoapi.CommandParseException;
import com.highmobility.autoapi.incoming.IncomingCommand;
import com.highmobility.hmkit.ConnectedLink;
import com.highmobility.hmkit.ConnectedLinkListener;
import com.highmobility.hmkit.Error.LinkError;
import com.highmobility.hmkit.Link;

import org.amv.access.sdk.spi.bluetooth.ConnectionState;
import org.amv.access.sdk.spi.bluetooth.ConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.IncomingCommandEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleConnectionStateChangeEvent;
import org.amv.access.sdk.spi.bluetooth.impl.SimpleIncomingCommandEvent;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

import static com.google.common.base.Preconditions.checkNotNull;

public class HmBluetoothConnection implements BluetoothConnection {
    private static final String TAG = "HmBluetoothConnection";

    private final ConnectedLink connectedLink;
    private final PublishSubject<ConnectionStateChangeEvent> linkStateSubject;
    private final PublishSubject<IncomingCommandEvent> incomingCommandsSubject;

    HmBluetoothConnection(ConnectedLink connectedLink) {
        this.connectedLink = checkNotNull(connectedLink);
        this.linkStateSubject = PublishSubject.create();

        this.incomingCommandsSubject = PublishSubject.create();

        this.connectedLink.setListener(new HmBtleConnectedLinkListener());
    }

    @Override
    public Observable<Boolean> sendCommand(byte[] command) {
        return Observable.<Boolean>unsafeCreate(subscriber -> {
            connectedLink.sendCommand(command, new Link.CommandCallback() {
                @Override
                public void onCommandSent() {
                    subscriber.onNext(true);
                    subscriber.onComplete();
                }

                @Override
                public void onCommandFailed(LinkError linkError) {
                    subscriber.onError(new RuntimeException(linkError.getType() + ": " + linkError.getMessage()));
                }
            });
        }).timeout(15, TimeUnit.SECONDS);
    }

    @Override
    public Observable<ConnectionStateChangeEvent> observeConnectionState() {
        return this.linkStateSubject.share();
    }

    @Override
    public Observable<IncomingCommandEvent> observeIncomingCommands() {
        return incomingCommandsSubject.share();
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
        public void onCommandReceived(Link link, byte[] bytes) {
            Optional<IncomingCommand> commandOptional = parseIncomingCommand(bytes);
            if (!commandOptional.isPresent()) {
                Log.w(TAG, "Unknown or erroneous command received - ignoring.");
                return;
            }

            IncomingCommand command = commandOptional.get();

            Log.d(TAG, "Command received: " + command
                    .getIdentifier()
                    .name());

            incomingCommandsSubject.onNext(SimpleIncomingCommandEvent.builder()
                    .command(bytes)
                    .build());
        }


        private Optional<IncomingCommand> parseIncomingCommand(byte[] bytes) {
            try {
                return Optional.of(IncomingCommand.create(bytes));
            } catch (CommandParseException e) {
                Log.w(TAG, "Error while parsing command", e);
                return Optional.absent();
            }
        }
    }
}
