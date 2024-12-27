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
        if (outpostIvBack != null) {
            outpostIvBack.setOnClickListener(v -> finish());
        }
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
            if (device != null && bluetoothComm != null) {
                bluetoothComm.connect(device);
            } else {
                handler.post(() -> showToastAndFinish("无法获取蓝牙设备"));
            }
        }).start();
    }

    private void setupListeners() {
        if (outpostSwitchSetting1 != null) {
            outpostSwitchSetting1.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        }
        if (outpostSwitchSetting2 != null) {
            outpostSwitchSetting2.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        }
        if (outpostSwitchSetting3 != null) {
            outpostSwitchSetting3.setOnCheckedChangeListener((v, checked) -> sendCurrentState());
        }

        if (outpostSeekBar != null) {
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
                        if (outpostEtHealthValue != null) {
                            outpostEtHealthValue.setText("血量：" + actualProgress);
                        }
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
    }

    private void sendCurrentState() {
        if (bluetoothComm == null) {
            handler.post(() -> Toast.makeText(this, "蓝牙通信未初始化", Toast.LENGTH_SHORT).show());
            return;
        }

        new Thread(() -> {
            boolean setting1 = outpostSwitchSetting1 != null && outpostSwitchSetting1.isChecked();
            boolean setting2 = outpostSwitchSetting2 != null && outpostSwitchSetting2.isChecked();
            boolean setting3 = outpostSwitchSetting3 != null && outpostSwitchSetting3.isChecked();
            int progress = outpostSeekBar != null ? outpostSeekBar.getProgress() * 100 : 0;

            bluetoothComm.sendData(setting1, setting2, setting3, progress);
        }).start();
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

    @SuppressLint("SetTextI18n")
    @Override
    public void onDataReceived(String data) {
        if (data == null || data.isEmpty()) {
            handler.post(() -> Toast.makeText(this, "接收到的数据为空", Toast.LENGTH_SHORT).show());
            return;
        }

        String[] parts = data.split(", ");
        if (parts.length < 4) {
            handler.post(() -> Toast.makeText(this, "数据格式不正确", Toast.LENGTH_SHORT).show());
            return;
        }

        handler.post(() -> {
            if (outpostSwitchSetting1 != null) {
                outpostSwitchSetting1.setChecked(!parts[0].contains("开启"));
            }
            if (outpostSwitchSetting2 != null) {
                outpostSwitchSetting2.setChecked(parts[1].contains("蓝色"));
            }
            if (outpostSwitchSetting3 != null) {
                outpostSwitchSetting3.setChecked(parts[2].contains("顺时针"));
            }
            if (outpostSeekBar != null && outpostEtHealthValue != null) {
                try {
                    int health = Integer.parseInt(parts[3].split(": ")[1]);
                    outpostSeekBar.setProgress(health / 100);
                    outpostEtHealthValue.setText("血量：" + health);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "解析血量数据失败", Toast.LENGTH_SHORT).show();
                }
            }
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