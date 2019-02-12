package org.amv.access.sdk.hm.vehicle;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.highmobility.autoapi.ChargeState;
import com.highmobility.autoapi.Command;
import com.highmobility.autoapi.DiagnosticsState;
import com.highmobility.autoapi.KeyfobPosition;
import com.highmobility.autoapi.LockState;
import com.highmobility.autoapi.VehicleStatus;
import com.highmobility.autoapi.property.ChargingState;
import com.highmobility.autoapi.property.doors.DoorLockAndPositionState;
import com.highmobility.autoapi.property.doors.DoorPosition;

import org.amv.access.sdk.spi.vehicle.VehicleState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleChargingPlugState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleDoorLockState;
import org.amv.access.sdk.spi.vehicle.impl.SimpleDoorsPosition;
import org.amv.access.sdk.spi.vehicle.impl.SimpleKeyPosition;
import org.amv.access.sdk.spi.vehicle.impl.SimpleMileage;

import java.util.List;

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

    public HmVehicleState extend(com.highmobility.autoapi.Command command) {
        HmVehicleStateBuilder builder = this.toBuilder();

        if (command.is(LockState.TYPE)) {
            LockState lockState = (LockState) command;

            List<DoorLockAndPositionState> doorLockAndPositionStates = Lists.newArrayList(lockState.getDoorLockAndPositionStates());
            boolean anyDoorOpen = false;
            for (DoorLockAndPositionState doorState : doorLockAndPositionStates) {
                if (doorState.getDoorPosition() == DoorPosition.OPEN) {
                    anyDoorOpen = true;
                    break;
                }
            }

            builder
                    .doorLocksState(SimpleDoorLockState.builder()
                            .locked(lockState.isLocked())
                            .build())
                    .doorsPositionState(SimpleDoorsPosition.builder()
                            .open(anyDoorOpen)
                            .build());

        } else if (command.is(ChargeState.TYPE)) {
            ChargeState state = (ChargeState) command;
            boolean notPluggedIn = state.getChargingState() == ChargingState.DISCONNECTED;

            builder.chargingPlugState(SimpleChargingPlugState.builder()
                    .plugged(!notPluggedIn)
                    .build());
        } else if (command.is(DiagnosticsState.TYPE)) {
            DiagnosticsState state = (DiagnosticsState) command;
            int mileage = state.getMileage();

            builder.mileage(SimpleMileage.builder()
                    .value(mileage)
                    .build());
        } else if (command.is(KeyfobPosition.TYPE)) {
            KeyfobPosition state = (KeyfobPosition) command;
            boolean keyPresent = state.getKeyfobPosition() == com.highmobility.autoapi.property.KeyfobPosition.INSIDE_CAR;

            builder.keyPosition(SimpleKeyPosition.builder()
                    .known(keyPresent)
                    .build());
        } else if (command.is(VehicleStatus.TYPE)) {
            VehicleStatus vehicleStatus = (VehicleStatus) command;
            Command[] statesOrNull = vehicleStatus.getStates();
            Command[] states = statesOrNull == null ? new Command[0] : statesOrNull;

            return extend(states).build();
        }

        return builder.build();
    }

    private HmVehicleStateBuilder extend(Command[] featureStates) {
        HmVehicleStateBuilder builder = this.toBuilder();
        for (Command featureState : featureStates) {
            builder = extend(builder, featureState);
        }
        return builder;
    }

    private HmVehicleStateBuilder extend(HmVehicleStateBuilder builder, Command featureState) {
        if (featureState.is(LockState.TYPE)) {
            LockState lockState = (LockState) featureState;

            List<DoorLockAndPositionState> doorLockAndPositionStates = Lists.newArrayList(lockState.getDoorLockAndPositionStates());
            boolean anyDoorOpen = false;
            for (DoorLockAndPositionState doorState : doorLockAndPositionStates) {
                if (doorState.getDoorPosition() == DoorPosition.OPEN) {
                    anyDoorOpen = true;
                    break;
                }
            }

            builder.doorLocksState(SimpleDoorLockState.builder()
                    .locked(lockState.isLocked())
                    .build())
                    .doorsPositionState(SimpleDoorsPosition.builder()
                            .open(anyDoorOpen)
                            .build());
        } else if (featureState.is(ChargeState.TYPE)) {
            ChargeState state = (ChargeState) featureState;
            boolean notPluggedIn = state.getChargingState() == ChargingState.DISCONNECTED;

            builder.chargingPlugState(SimpleChargingPlugState.builder()
                    .plugged(!notPluggedIn)
                    .build());
        } else if (featureState.is(DiagnosticsState.TYPE)) {
            DiagnosticsState state = (DiagnosticsState) featureState;
            int mileage = state.getMileage();

            builder.mileage(SimpleMileage.builder()
                    .value(mileage)
                    .build());
        } else if (featureState.is(KeyfobPosition.TYPE)) {
            KeyfobPosition state = (KeyfobPosition) featureState;
            boolean keyPresent = state.getKeyfobPosition() == com.highmobility.autoapi.property.KeyfobPosition.INSIDE_CAR;

            builder.keyPosition(SimpleKeyPosition.builder()
                    .known(keyPresent)
                    .build());
        }

        return builder;
    }
}
