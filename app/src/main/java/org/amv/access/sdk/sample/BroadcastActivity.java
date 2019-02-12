package org.amv.access.sdk.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.squareup.leakcanary.RefWatcher;

import org.amv.access.sdk.sample.logic.IBluetoothController;
import org.amv.access.sdk.sample.logic.IBluetoothView;
import org.amv.access.sdk.spi.error.AccessSdkException;
import org.amv.access.sdk.spi.identity.SerialNumber;
import org.amv.access.sdk.spi.vehicle.VehicleState;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import dagger.android.AndroidInjection;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class BroadcastActivity extends Activity implements IBluetoothView {
    public static final String ACCESS_CERT_ID_IDENTIFIER = "ACCESS_CERT_ID_IDENTIFIER";

    @BindView(R.id.title_button)
    TextView titleText;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.progress_bar_subtext)
    TextView progressBarSubtext;

    @BindView(R.id.connected_view)
    RelativeLayout connectedView;

    @BindView(R.id.lock_button)
    Button lockButton;
    @BindView(R.id.request_vehicle_state_button)
    Button requestVehicleStateButton;
    @BindView(R.id.disconnect_button)
    Button disconnectButton;
    @BindView(R.id.disconnect_and_stop_broadcasting_button)
    Button disconnectAndStopBroadcastingButton;

    @BindView(R.id.lock_state_text_view)
    TextView lockStateText;

    @BindView(R.id.mileage_text_view)
    TextView mileageTextView;
    @BindView(R.id.charging_plug_switch)
    Switch chargingPlugSwitch;
    @BindView(R.id.key_switch)
    Switch keySwitch;
    @BindView(R.id.doors_switch)
    Switch doorsSwitch;

    @Inject
    IBluetoothController controller;

    private String accessCertId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_broadcast);
        ButterKnife.bind(this);

        String givenAccessCertId = getIntent().getExtras().getString(ACCESS_CERT_ID_IDENTIFIER, null);
        if (givenAccessCertId == null) {
            throw new IllegalStateException("No access certificate id provided to activity");
        } else {
            this.accessCertId = givenAccessCertId;
        }

        this.controller.initialize(this, getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        controller.onDestroy();

        RefWatcher refWatcher = AccessDemoApplication.getRefWatcher(this);
        refWatcher.watch(this);
        refWatcher.watch(controller);
    }

    @Override
    public void onInitializeFinished() {
        controller.connect(this.accessCertId);

        lockButton.setOnClickListener(view -> controller.lockUnlockDoors());
        requestVehicleStateButton.setOnClickListener(view -> controller.requestVehicleState());
        disconnectButton.setOnClickListener(view -> controller.sendDisconnectCommand());
        disconnectAndStopBroadcastingButton.setOnClickListener(view -> {
            this.finish();
        });

        setSwitchColor(chargingPlugSwitch);
        setSwitchColor(keySwitch);
        setSwitchColor(doorsSwitch);
    }

    @Override
    public void onInitializeFailed(AccessSdkException error) {
        String title = getString(R.string.initialization_error) + ": " + error.getType().getName();
        String message = error.getMessage();

        finishWithMessage(title, message);
    }

    @Override
    public void setVehicleSerial(SerialNumber vehicleSerial) {
        titleText.setText(vehicleSerial.getSerialNumberHex());
    }

    @Override
    public void onStateUpdate(IBluetoothController.State state) {
        if (state == IBluetoothController.State.VEHICLE_READY
                || state == IBluetoothController.State.VEHICLE_UPDATING) {
            connectedView.setVisibility(VISIBLE);
        } else {
            connectedView.setVisibility(GONE);
        }

        switch (state) {
            case BLE_NOT_AVAILABLE: {
                updateProgressBarSubtext(true, getString(R.string.bluetooth_not_available));
                break;
            }
            case IDLE: {
                updateProgressBarSubtext(true, getString(R.string.idle));
                break;
            }
            case LOOKING: {
                showProgressBar(true);
                updateProgressBarSubtext(true, getString(R.string.broadcasting));
                break;
            }
            case VEHICLE_CONNECTED: {
                showProgressBar(true);
                updateProgressBarSubtext(true, getString(R.string.vehicle_connected));
                break;
            }
            case VEHICLE_READY: {
                showProgressBar(false);
                updateProgressBarSubtext(false, null);
                enableButtons();
                break;
            }
            case VEHICLE_UPDATING: {
                updateProgressBarSubtext(true, getString(R.string.updating));
                showProgressBar(true);
                disableButtons();
                break;
            }
        }
    }

    private void enableButtons() {
        lockButton.setEnabled(true);
        requestVehicleStateButton.setEnabled(true);
        disconnectButton.setEnabled(true);
        disconnectAndStopBroadcastingButton.setEnabled(true);
    }

    private void disableButtons() {
        lockButton.setEnabled(false);
        requestVehicleStateButton.setEnabled(false);
        disconnectButton.setEnabled(false);
        disconnectAndStopBroadcastingButton.setEnabled(false);
    }

    @Override
    public void onVehicleStatusUpdate(VehicleState status) {
        if (status.getMileage().isPresent()) {
            mileageTextView.setText(String.valueOf(status.getMileage().get().getValue()));
        } else {
            mileageTextView.setText(R.string.unknown_mileage_text);
        }

        if (status.getDoorLockState().isPresent()) {
            lockStateText.setVisibility(VISIBLE);
            if (status.getDoorLockState().get().isLocked()) {
                lockButton.setText(R.string.unlock_doors);
                lockStateText.setText(R.string.doors_locked);
            } else {
                lockButton.setText(R.string.lock_doors);
                lockStateText.setText(R.string.doors_unlocked);
            }
        }

        // checked is red
        if (status.getDoorPositionState().isPresent()) {
            doorsSwitch.setEnabled(true); // open red, closed green

            if (status.getDoorPositionState().get().isOpen()) {
                doorsSwitch.setChecked(false);
            } else {
                doorsSwitch.setChecked(true);
            }
        }

        if (status.getKeyPosition().isPresent()) {
            keySwitch.setEnabled(true);
            // key green inserted, red not inserted
            if (status.getKeyPosition().get().isKnown()) {
                keySwitch.setChecked(true);
            } else {
                keySwitch.setChecked(false);
            }
        }

        if (status.getChargingPlugState().isPresent()) {
            chargingPlugSwitch.setEnabled(true);
            if (status.getChargingPlugState().get().isUnplugged()) {
                chargingPlugSwitch.setChecked(false);
            } else {
                chargingPlugSwitch.setChecked(true);
            }
        }
    }

    @Override
    public void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    @Override
    public void finishWithMessage(String title, String message) {
        showProgressBar(false);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), (dialog, which) -> finish())
                .show();
    }

    private void updateProgressBarSubtext(boolean show, String text) {
        showNormalView(show == false);

        if (show && progressBarSubtext.getVisibility() != VISIBLE) {
            progressBarSubtext.animate().alpha(1f).setDuration(200).setListener(null);
        }
        progressBarSubtext.setVisibility(show ? VISIBLE : GONE);
        progressBarSubtext.setText(text);
    }

    public void showProgressBar(boolean show) {
        if (show) {
            progressBar.animate().alpha(1f).setDuration(200).setListener(null);
            connectedView.animate().alpha(.7f).setDuration(200).setListener(null);
        } else {
            connectedView.animate().alpha(1f).setDuration(200).setListener(null);
            progressBar.animate().alpha(0f).setDuration(200).setListener(null);
        }
    }

    private void showNormalView(boolean show) {
        if (show) {
            connectedView.animate().alpha(1f).setDuration(200).setListener(null);
            progressBarSubtext.animate().alpha(0f).setDuration(200).setListener(null);
        } else {
            progressBarSubtext.animate().alpha(1f).setDuration(200).setListener(null);
            connectedView.animate().alpha(0f).setDuration(200).setListener(null);
        }
    }

    private void setSwitchColor(Switch switchInput) {
        if (Build.VERSION.SDK_INT >= 24) {
            ColorStateList thumbStates = new ColorStateList(
                    new int[][]{
                            new int[]{}
                    },
                    new int[]{
                            ContextCompat.getColor(this, R.color.lightClay)
                    }
            );
            switchInput.setThumbTintList(thumbStates);

            ColorStateList trackStates = new ColorStateList(
                    new int[][]{
                            new int[]{-android.R.attr.state_enabled},
                            new int[]{android.R.attr.state_checked},
                            new int[]{}
                    },
                    new int[]{
                            ContextCompat.getColor(this, R.color.lightClay),
                            Color.GREEN,
                            Color.RED
                    }
            );

            switchInput.setTrackTintMode(PorterDuff.Mode.OVERLAY);
            switchInput.setTrackTintList(trackStates);
        }
    }
}
