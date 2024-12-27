package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.wdr_outpost.bluetooth.BluetoothCommunicator;

public class OutpostDeviceActivity extends AppCompatActivity implements BluetoothCommunicator.BluetoothCallback {
    private static final int MIN_PROGRESS = 400;
    private static final int MAX_PROGRESS = 5000;

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch outpostSwitchSetting1, outpostSwitchSetting2, outpostSwitchSetting3;
    private SeekBar outpostSeekBar;
    private TextView outpostEtHealthValue;
    private BluetoothCommunicator bluetoothComm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outpost);

        initializeUI();
        initializeBluetooth();
        setupListeners();
    }

    private void initializeUI() {
        outpostSwitchSetting1 = findViewById(R.id.outpost_sw_setting_1);
        outpostSwitchSetting2 = findViewById(R.id.outpost_sw_setting_2);
        outpostSwitchSetting3 = findViewById(R.id.outpost_sw_setting_3);
        outpostSeekBar = findViewById(R.id.outpost_sb_setting_progress);
        outpostEtHealthValue = findViewById(R.id.outpost_tv_setting_name_4);

        ImageView outpostIvBack = findViewById(R.id.outpost_iv_back);
        outpostIvBack.setOnClickListener(v -> finish());
    }

    private void initializeBluetooth() {
        new Thread(() -> {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null || bluetoothManager.getAdapter() == null) {
                handler.post(() -> showToastAndFinish("蓝牙不可用"));
                return;
            }

            String deviceAddress = getIntent().getStringExtra("deviceMacAddress");
            if (deviceAddress == null) {
                handler.post(() -> showToastAndFinish("未提供设备地址"));
                return;
            }

            bluetoothComm = new BluetoothCommunicator(this);
            BluetoothDevice device = bluetoothManager.getAdapter().getRemoteDevice(deviceAddress);
            bluetoothComm.connect(device);
        }).start();
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
                    if (actualProgress < MIN_PROGRESS) {
                        actualProgress = MIN_PROGRESS;
                        seekBar.setProgress(MIN_PROGRESS / 100);
                    } else if (actualProgress > MAX_PROGRESS) {
                        actualProgress = MAX_PROGRESS;
                        seekBar.setProgress(MAX_PROGRESS / 100);
                    }
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
        new Thread(() -> bluetoothComm.sendData(
                outpostSwitchSetting1.isChecked(),
                outpostSwitchSetting2.isChecked(),
                outpostSwitchSetting3.isChecked(),
                outpostSeekBar.getProgress() * 100
        )).start();
    }

    // BluetoothCallback implementations
    @Override
    public void onConnected() {
        handler.post(() -> Toast.makeText(this, "已连接", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onConnectionFailed(String error) {
        handler.post(() -> {
            Toast.makeText(this, "连接失败: " + error, Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    public void onDataReceived(String data) {
        // Parse received data and update UI
        String[] parts = data.split(", ");
        if (parts.length < 4) return;

        handler.post(() -> {
            outpostSwitchSetting1.setChecked(parts[0].contains("开启"));
            outpostSwitchSetting2.setChecked(parts[1].contains("蓝色"));
            outpostSwitchSetting3.setChecked(parts[2].contains("顺时针"));
            int health = Integer.parseInt(parts[3].split(": ")[1]);
            outpostSeekBar.setProgress(health / 100);
            outpostEtHealthValue.setText(String.valueOf(health));
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
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothComm != null) {
            bluetoothComm.disconnect();
        }
    }
}