package org.amv.access.sdk.hm.bluetooth;

import android.util.Log;

import com.google.common.base.Optional;
import com.highmobility.autoapi.CommandParseException;
import com.highmobility.autoapi.incoming.IncomingCommand;

final class MoreHmCommands {

    static Optional<IncomingCommand> parseIncomingCommand(byte[] bytes) {
        try {
            return Optional.of(IncomingCommand.create(bytes));
        } catch (CommandParseException e) {
            Log.w("", "Error while parsing command", e);
            return Optional.absent();
        }
    }

    private MoreHmCommands() {
        throw new UnsupportedOperationException();
    }
}
