package org.amv.access.sdk.hm.communication;

import com.highmobility.autoapi.GetVehicleStatus;
import com.highmobility.autoapi.LockUnlockDoors;
import com.highmobility.autoapi.property.doors.DoorLock;

import org.amv.access.sdk.spi.communication.Command;
import org.amv.access.sdk.spi.communication.CommandFactory;
import org.amv.access.sdk.spi.communication.impl.SimpleCommand;

public class HmCommandFactory implements CommandFactory {
    @Override
    public Command lockDoors() {
        return new SimpleCommand("DOOR_LOCK", new LockUnlockDoors(DoorLock.LOCKED).getByteArray());
    }

    @Override
    public Command unlockDoors() {
        return new SimpleCommand("DOOR_UNLOCK", new LockUnlockDoors(DoorLock.UNLOCKED).getByteArray());
    }

    @Override
    public Command sendVehicleStatus() {
        return new SimpleCommand("SEND_VEHICLE_STATE", new GetVehicleStatus().getByteArray());
    }

    @Override
    public Command disconnect() {
        return new SimpleCommand("DISCONNECT", new byte[]{(byte) 0xFE});
    }

}
