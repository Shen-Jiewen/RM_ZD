package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class ClassicBluetoothCommunicationActivity extends AppCompatActivity implements BluetoothDataHandler {

    private static final String TAG = "ClassicBluetoothComm"; // 日志标签
    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // UI 组件
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchSetting1, switchSetting2, switchSetting3;
    private SeekBar seekBar;

    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;

    // 广播接收器，用于接收配对状态
    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (state == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "设备配对成功: " + bluetoothDevice.getAddress());
                    connectToDevice();
                } else if (state == BluetoothDevice.BOND_NONE) {
                    Log.e(TAG, "设备配对失败: " + bluetoothDevice.getAddress());
                    Toast.makeText(ClassicBluetoothCommunicationActivity.this, "配对失败", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        initializeUIComponents();
        initializeBluetoothComponents();
        setupSwitchListeners();
        setupSeekBarListener();
    }

    @SuppressLint("WrongViewCast")
    private void initializeUIComponents() {
        switchSetting1 = findViewById(R.id.sw_setting_1);
        switchSetting2 = findViewById(R.id.sw_setting_2);
        switchSetting3 = findViewById(R.id.sw_setting_3);
        seekBar = findViewById(R.id.sb_setting_progress);
    }

    private void initializeBluetoothComponents() {
        // 蓝牙相关变量
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            showToastAndFinish("无法获取 BluetoothManager");
            return;
        }

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            showToastAndFinish("无法获取 BluetoothAdapter");
            return;
        }

        String deviceMacAddress = getIntent().getStringExtra("deviceMacAddress");
        if (deviceMacAddress != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMacAddress);
            if (bluetoothDevice != null) {
                connectToDevice();
            } else {
                showToastAndFinish("无法获取蓝牙设备");
            }
        }
    }

    private void setupSwitchListeners() {
        switchSetting1.setOnCheckedChangeListener((buttonView, isChecked) -> sendData("Switch1:" + (isChecked ? "ON" : "OFF")));
        switchSetting2.setOnCheckedChangeListener((buttonView, isChecked) -> sendData("Switch2:" + (isChecked ? "ON" : "OFF")));
        switchSetting3.setOnCheckedChangeListener((buttonView, isChecked) -> sendData("Switch3:" + (isChecked ? "ON" : "OFF")));
    }

    private void setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    sendData("SeekBar:" + progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(DEFAULT_UUID);
            Log.d(TAG, "尝试连接经典蓝牙设备: " + bluetoothDevice.getAddress());
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "经典蓝牙设备连接成功: " + bluetoothDevice.getAddress());

            startDataReceiveThread();
        } catch (IOException e) {
            Log.e(TAG, "经典蓝牙设备连接失败: " + e.getMessage(), e);
            Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void startDataReceiveThread() {
        receiveThread = new Thread(this::receiveData);
        receiveThread.start();
    }

    private void receiveData() {
        byte[] buffer = new byte[1024];
        int bytes;
        while (true) {
            try {
                bytes = inputStream.read(buffer);
                String message = new String(buffer, 0, bytes);
                Log.d(TAG, "接收消息: " + message);

                // 解析接收到的数据并更新UI
                parseReceivedData(message);
            } catch (IOException e) {
                Log.e(TAG, "数据接收失败: " + e.getMessage());
                break;
            }
        }
    }

    @Override
    public void sendData(String data) {
        try {
            outputStream.write(data.getBytes());
            Log.d(TAG, "发送消息: " + data);
        } catch (IOException e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "发送消息失败: " + e.getMessage());
        }
    }

    @Override
    public void parseReceivedData(String data) {

    }

    private void showToastAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeBluetoothSocket();
        stopDataReceiveThread();
    }

    private void closeBluetoothSocket() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy 中发生异常: " + e.getMessage(), e);
        }
    }

    private void stopDataReceiveThread() {
        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
        }
    }
}