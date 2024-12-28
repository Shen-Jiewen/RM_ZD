package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wdr_outpost.device.DeviceManager;
import com.example.wdr_outpost.protocol.OutpostProtocol;

public class OutpostDeviceActivity extends AppCompatActivity implements DeviceManager.DeviceCallback<OutpostProtocol.OutpostData> {
    private static final int MIN_PROGRESS = 400;
    private static final int MAX_PROGRESS = 5000;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch outpostSwitchSetting1, outpostSwitchSetting2, outpostSwitchSetting3;
    private SeekBar outpostSeekBar;
    private TextView outpostEtHealthValue;
    private DeviceManager<OutpostProtocol.OutpostData> deviceManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outpost);

        initializeUI();
        initializeDevice();
        setupListeners();

        // 恢复保存的状态
        if (savedInstanceState != null) {
            outpostSwitchSetting1.setChecked(savedInstanceState.getBoolean("switch1"));
            outpostSwitchSetting2.setChecked(savedInstanceState.getBoolean("switch2"));
            outpostSwitchSetting3.setChecked(savedInstanceState.getBoolean("switch3"));
            outpostSeekBar.setProgress(savedInstanceState.getInt("seekBarProgress"));
        }
    }

    private void initializeUI() {
        outpostSwitchSetting1 = findViewById(R.id.outpost_sw_setting_1);
        outpostSwitchSetting2 = findViewById(R.id.outpost_sw_setting_2);
        outpostSwitchSetting3 = findViewById(R.id.outpost_sw_setting_3);
        outpostSeekBar = findViewById(R.id.outpost_sb_setting_progress);
        outpostEtHealthValue = findViewById(R.id.outpost_tv_setting_name_4);

        findViewById(R.id.outpost_iv_back).setOnClickListener(v -> finish());
    }

    private void initializeDevice() {
        deviceManager = new DeviceManager<>(new OutpostProtocol(), this);

        String deviceAddress = getIntent().getStringExtra("deviceMacAddress");
        if (deviceAddress == null) {
            showToastAndFinish("未提供设备地址");
            return;
        }

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null || bluetoothManager.getAdapter() == null) {
            showToastAndFinish("蓝牙不可用");
            return;
        }

        BluetoothDevice device = bluetoothManager.getAdapter().getRemoteDevice(deviceAddress);
        if (device != null) {
            new Thread(() -> deviceManager.connect(device)).start();
        } else {
            showToastAndFinish("无法获取蓝牙设备");
        }
    }

    private void setupListeners() {
        outpostSwitchSetting1.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        outpostSwitchSetting2.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        outpostSwitchSetting3.setOnCheckedChangeListener((v, checked) -> sendCurrentState());

        outpostSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int actualProgress = progress * 100;
                    actualProgress = Math.max(MIN_PROGRESS, Math.min(MAX_PROGRESS, actualProgress));
                    seekBar.setProgress(actualProgress / 100);
                    outpostEtHealthValue.setText("血量：" + actualProgress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                sendCurrentState();
            }
        });
    }

    private void sendCurrentState() {
        OutpostProtocol.OutpostData data = new OutpostProtocol.OutpostData(
                outpostSwitchSetting1.isChecked(),
                outpostSwitchSetting2.isChecked(),
                outpostSwitchSetting3.isChecked(),
                outpostSeekBar.getProgress() * 100
        );
        new Thread(() -> deviceManager.sendData(data)).start();
    }

    @Override
    public void onConnected() {
        handler.post(() -> Toast.makeText(this, "已连接", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onConnectionFailed(String error) {
        handler.post(() -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDataReceived(OutpostProtocol.OutpostData data) {
        handler.post(() -> {
            outpostSwitchSetting1.setChecked(data.isOn());
            outpostSwitchSetting2.setChecked(data.isBlue());
            outpostSwitchSetting3.setChecked(data.isClockwise());
            outpostSeekBar.setProgress(data.getHealth() / 100);
            outpostEtHealthValue.setText("血量：" + data.getHealth());
        });
    }

    @Override
    public void onError(String error) {
        handler.post(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void showToastAndFinish(String message) {
        handler.post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("switch1", outpostSwitchSetting1.isChecked());
        outState.putBoolean("switch2", outpostSwitchSetting2.isChecked());
        outState.putBoolean("switch3", outpostSwitchSetting3.isChecked());
        outState.putInt("seekBarProgress", outpostSeekBar.getProgress());
        String deviceAddress = getIntent().getStringExtra("deviceMacAddress");
        outState.putString("deviceMacAddress", deviceAddress);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        outpostSwitchSetting1.setChecked(savedInstanceState.getBoolean("switch1"));
        outpostSwitchSetting2.setChecked(savedInstanceState.getBoolean("switch2"));
        outpostSwitchSetting3.setChecked(savedInstanceState.getBoolean("switch3"));
        outpostSeekBar.setProgress(savedInstanceState.getInt("seekBarProgress"));
        String deviceAddress = savedInstanceState.getString("deviceMacAddress");
        if (deviceAddress != null) {
            getIntent().putExtra("deviceMacAddress", deviceAddress);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceManager != null) {
            deviceManager.disconnect();
        }
    }
}