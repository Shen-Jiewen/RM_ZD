package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wdr_outpost.bluetooth.BluetoothCommunicator;

public class OutpostDeviceActivity extends AppCompatActivity implements BluetoothCommunicator.BluetoothCallback {
    private static final int MIN_PROGRESS = 400;
    private static final int MAX_PROGRESS = 5000;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchSetting1, switchSetting2, switchSetting3;
    private SeekBar seekBar;
    private EditText etHealthValue;
    private BluetoothCommunicator bluetoothComm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        initializeUI();
        initializeBluetooth();
        setupListeners();
    }

    private void initializeUI() {
        switchSetting1 = findViewById(R.id.sw_setting_1);
        switchSetting2 = findViewById(R.id.sw_setting_2);
        switchSetting3 = findViewById(R.id.sw_setting_3);
        seekBar = findViewById(R.id.sb_setting_progress);
        etHealthValue = findViewById(R.id.et_health_value);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());
    }

    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null || bluetoothManager.getAdapter() == null) {
            showToastAndFinish("Bluetooth not available");
            return;
        }

        String deviceAddress = getIntent().getStringExtra("deviceMacAddress");
        if (deviceAddress == null) {
            showToastAndFinish("No device address provided");
            return;
        }

        bluetoothComm = new BluetoothCommunicator(this);
        BluetoothDevice device = bluetoothManager.getAdapter().getRemoteDevice(deviceAddress);
        bluetoothComm.connect(device);
    }

    private void setupListeners() {
        switchSetting1.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        switchSetting2.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        switchSetting3.setOnCheckedChangeListener((v, checked) -> sendCurrentState());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int actualProgress = progress * 100;
                    if (actualProgress < MIN_PROGRESS) {
                        actualProgress = MIN_PROGRESS;
                        seekBar.setProgress(MIN_PROGRESS / 100);
                    } else if (actualProgress > MAX_PROGRESS) {
                        actualProgress = MAX_PROGRESS;
                        seekBar.setProgress(MAX_PROGRESS / 100);
                    }
                    etHealthValue.setText(String.valueOf(actualProgress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendCurrentState();
            }
        });

        etHealthValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int value = Integer.parseInt(s.toString());
                    value = Math.max(MIN_PROGRESS, Math.min(value, MAX_PROGRESS));
                    seekBar.setProgress(value / 100);
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });
    }

    private void sendCurrentState() {
        bluetoothComm.sendData(
                switchSetting1.isChecked(),
                switchSetting2.isChecked(),
                switchSetting3.isChecked(),
                seekBar.getProgress() * 100
        );
    }

    // BluetoothCallback implementations
    @Override
    public void onConnected() {
        runOnUiThread(() -> Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onConnectionFailed(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Connection failed: " + error, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onDataReceived(String data) {
        // Parse received data and update UI
        String[] parts = data.split(", ");
        if (parts.length < 4) return;

        runOnUiThread(() -> {
            switchSetting1.setChecked(parts[0].contains("开启"));
            switchSetting2.setChecked(parts[1].contains("蓝色"));
            switchSetting3.setChecked(parts[2].contains("顺时针"));
            int health = Integer.parseInt(parts[3].split(": ")[1]);
            seekBar.setProgress(health / 100);
            etHealthValue.setText(String.valueOf(health));
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void showToastAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothComm != null) {
            bluetoothComm.disconnect();
        }
    }
}