package org.amv.access.sdk.hm.vehicle;

import com.google.common.base.Optional;
import com.highmobility.autoapi.Command;
import com.highmobility.autoapi.DoorLockState;
import com.highmobility.autoapi.incoming.ChargeState;
import com.highmobility.autoapi.incoming.DiagnosticsState;
import com.highmobility.autoapi.incoming.IncomingCommand;
import com.highmobility.autoapi.incoming.KeyfobPosition;
import com.highmobility.autoapi.incoming.LockState;
import com.highmobility.autoapi.incoming.VehicleStatus;
import com.highmobility.autoapi.vehiclestatus.Charging;
import com.highmobility.autoapi.vehiclestatus.Diagnostics;
import com.highmobility.autoapi.vehiclestatus.DoorLocks;
import com.highmobility.autoapi.vehiclestatus.FeatureState;

import org.amv.access.sdk.spi.vehicle.VehicleState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleChargingPlugState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleDoorLockState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleDoorsPosition;
import org.amv.access.sdk.spi.vehicle.impl.SimpleKeyPosition;
import org.amv.access.sdk.spi.vehicle.impl.SimpleMileage;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class HmVehicleState implements VehicleState {
    public static HmVehicleState unknown() {
        return HmVehicleState.builder()
                .build();
    }

    private DoorLocksState doorLocksState;
    private DoorsPositionState doorsPositionState;
    private ChargingPlugState chargingPlugState;
    private KeyPosition keyPosition;
    private Mileage mileage;

    @Override
    public Optional<DoorLocksState> getDoorLockState() {
        return Optional.fromNullable(doorLocksState);
    }

    @Override
    public Optional<DoorsPositionState> getDoorPositionState() {
        return Optional.fromNullable(doorsPositionState);
    }

    @Override
    public Optional<ChargingPlugState> getChargingPlugState() {
        return Optional.fromNullable(chargingPlugState);
    }

    @Override
    public Optional<KeyPosition> getKeyPosition() {
        return Optional.fromNullable(keyPosition);
    }

    @Override
    public Optional<Mileage> getMileage() {
        return Optional.fromNullable(mileage);
    }

    public HmVehicleState extend(IncomingCommand command) {
        HmVehicleStateBuilder builder = this.toBuilder();

        if (command.is(Command.DoorLocks.LOCK_STATE)) {
            LockState lockState = (LockState) command;

            boolean doorsOpen = lockState.getFrontLeft().getPosition() == DoorLockState.Position.OPEN
                    || lockState.getFrontRight().getPosition() == DoorLockState.Position.OPEN
                    || lockState.getRearLeft().getPosition() == DoorLockState.Position.OPEN
                    || lockState.getRearRight().getPosition() == DoorLockState.Position.OPEN;

            builder
                    .doorLocksState(SimpleDoorLockState.builder()
                            .locked(lockState.isLocked())
                            .build())
                    .doorsPositionState(SimpleDoorsPosition.builder()
                            .open(doorsOpen)
                            .build());

        } else if (command.is(Command.Charging.CHARGE_STATE)) {
            ChargeState state = (ChargeState) command;
            boolean notPluggedIn = state.getChargingState() == ChargeState.ChargingState.DISCONNECTED;

            builder.chargingPlugState(SimpleChargingPlugState.builder()
                    .plugged(!notPluggedIn)
                    .build());
        } else if (command.is(Command.Diagnostics.DIAGNOSTICS_STATE)) {
            DiagnosticsState state = (DiagnosticsState) command;
            int mileage = state.getMileage();

            builder.mileage(SimpleMileage.builder()
                    .value(mileage)
                    .build());
        } else if (command.is(Command.KeyfobPosition.KEYFOB_POSITION)) {
            KeyfobPosition state = (KeyfobPosition) command;
            boolean keyPresent = state.getPosition() == KeyfobPosition.Position.INSIDE_CAR;

            builder.keyPosition(SimpleKeyPosition.builder()
                    .known(keyPresent)
                    .build());
        } else if (command.is(Command.VehicleStatus.VEHICLE_STATUS)) {
            VehicleStatus vehicleStatus = (VehicleStatus) command;
            FeatureState[] statesOrNull = vehicleStatus.getFeatureStates();
            FeatureState[] states = statesOrNull == null ? new FeatureState[0] : statesOrNull;

            return extend(states).build();
        }

        return builder.build();
    }

    private HmVehicleStateBuilder extend(FeatureState[] featureStates) {
        HmVehicleStateBuilder builder = this.toBuilder();
        for (FeatureState featureState : featureStates) {
            builder = extend(builder, featureState);
        }
        return builder;
    }

    private HmVehicleStateBuilder extend(HmVehicleStateBuilder builder, FeatureState featureState) {
        if (featureState.getIdentifier() == Command.Identifier.DOOR_LOCKS) {
            DoorLocks lockState = (DoorLocks) featureState;
            boolean doorsOpen =
                    lockState.getFrontLeft().getPosition() == DoorLockState.Position.OPEN
                            || lockState.getFrontRight().getPosition() == DoorLockState.Position.OPEN
                            || lockState.getRearLeft().getPosition() == DoorLockState.Position.OPEN
                            || lockState.getRearRight().getPosition() == DoorLockState.Position.OPEN;

            builder.doorLocksState(SimpleDoorLockState.builder()
                    .locked(lockState.isLocked())
                    .build())
                    .doorsPositionState(SimpleDoorsPosition.builder()
                            .open(doorsOpen)
                            .build());
        } else if (featureState.getIdentifier() == Command.Identifier.CHARGING) {
            Charging state = (Charging) featureState;
            boolean notPluggedIn = state.getChargingState() == ChargeState.ChargingState.DISCONNECTED;
            builder.chargingPlugState(SimpleChargingPlugState.builder()
                    .plugged(!notPluggedIn)
                    .build());
        } else if (featureState.getIdentifier() == Command.Identifier.DIAGNOSTICS) {
            int mileage = ((Diagnostics) featureState).getMileage();
            builder.mileage(SimpleMileage.builder()
                    .value(mileage)
                    .build());
        } else if (featureState.getIdentifier() == Command.Identifier.KEYFOB_POSITION) {
            com.highmobility.autoapi.vehiclestatus.KeyfobPosition keyfobPosition =
                    ((com.highmobility.autoapi.vehiclestatus.KeyfobPosition) featureState);

            boolean keyPresent = keyfobPosition.getPosition() ==
                    KeyfobPosition.Position.INSIDE_CAR;

            builder.keyPosition(SimpleKeyPosition.builder()
                    .known(keyPresent)
                    .build());
        }

        return builder;
    }
}
