package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class ClassicBluetoothCommunicationActivity extends AppCompatActivity implements BluetoothDataHandler {

    private static final int MIN_PROGRESS = 400;  // 最小进度值
    private static final int MAX_PROGRESS = 5000;  // 最大进度值

    private static final String TAG = "ClassicBluetoothComm"; // 日志标签
    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // UI 组件
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch switchSetting1, switchSetting2, switchSetting3;
    private SeekBar seekBar;
    private EditText etHealthValue;

    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        initializeUIComponents();
        initializeBluetoothComponents();
        setupSwitchListeners();
        setupSeekBarListener();

        // 设置返回键的逻辑
        setupBackPressedCallback();
    }

    private void setupBackPressedCallback() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 在这里处理返回逻辑
                closeBluetoothSocket();
                stopDataReceiveThread();

                // 关闭当前 Activity
                finish();
            }
        };

        // 将回调添加到 OnBackPressedDispatcher
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @SuppressLint("WrongViewCast")
    private void initializeUIComponents() {
        switchSetting1 = findViewById(R.id.sw_setting_1);
        switchSetting2 = findViewById(R.id.sw_setting_2);
        switchSetting3 = findViewById(R.id.sw_setting_3);
        seekBar = findViewById(R.id.sb_setting_progress);
        etHealthValue = findViewById(R.id.et_health_value);

        // 初始化返回键
        ImageView ivBack = findViewById(R.id.iv_back);

        // 设置返回键的点击事件
        ivBack.setOnClickListener(v -> {
            // 触发 OnBackPressedCallback 的逻辑
            getOnBackPressedDispatcher().onBackPressed();
        });
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
        switchSetting1.setOnCheckedChangeListener((buttonView, isChecked) -> sendData(isChecked, switchSetting2.isChecked(), switchSetting3.isChecked(), seekBar.getProgress()));
        switchSetting2.setOnCheckedChangeListener((buttonView, isChecked) -> sendData(switchSetting1.isChecked(), isChecked, switchSetting3.isChecked(), seekBar.getProgress()));
        switchSetting3.setOnCheckedChangeListener((buttonView, isChecked) -> sendData(switchSetting1.isChecked(), switchSetting2.isChecked(), isChecked, seekBar.getProgress()));
    }

    private void setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 将 progress 乘以 100
                    int actualProgress = progress * 100;

                    // 限制进度条的范围在 20%-80%（即 1000-4000）
                    if (actualProgress < MIN_PROGRESS) {
                        actualProgress = MIN_PROGRESS;
                        seekBar.setProgress(MIN_PROGRESS / 100);
                    } else if (actualProgress > MAX_PROGRESS) {
                        actualProgress = MAX_PROGRESS;
                        seekBar.setProgress(MAX_PROGRESS / 100);
                    }

                    // 更新 EditText 的值
                    etHealthValue.setText(String.valueOf(actualProgress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 不需要处理
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 在松开触摸时触发数据发送
                int progress = seekBar.getProgress() * 100; // 将 progress 乘以 100
                sendData(switchSetting1.isChecked(), switchSetting2.isChecked(), switchSetting3.isChecked(), progress);
            }
        });

        // 添加 EditText 的监听器
        etHealthValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要处理
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要处理
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int value = Integer.parseInt(s.toString());
                    if (value < MIN_PROGRESS) {
                        value = MIN_PROGRESS;
                        etHealthValue.setText(String.valueOf(MIN_PROGRESS));
                    } else if (value > MAX_PROGRESS) {
                        value = MAX_PROGRESS;
                        etHealthValue.setText(String.valueOf(MAX_PROGRESS));
                    }
                    seekBar.setProgress(value / 100); // 将 value 除以 100
                } catch (NumberFormatException e) {
                    // 忽略非数字输入
                }
            }
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
                if (bytes > 0) {
                    String message = parseFrame(buffer, bytes);
                    Log.d(TAG, "接收消息: " + message);
                    parseReceivedData(message);
                }
            } catch (IOException e) {
                Log.e(TAG, "数据接收失败: " + e.getMessage());
                break;
            }
        }
    }

    @Override
    public void sendData(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        try {
            byte[] frame = buildFrame(isOn, isBlue, isClockwise, health);
            outputStream.write(frame);
            Log.d(TAG, "发送消息: " + bytesToHex(frame));
        } catch (IOException e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "发送消息失败: " + e.getMessage());
        }
    }

    private byte[] buildFrame(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        // 状态字节：开启状态（位 3），颜色（位 2），旋转方向（位 1）
        int statusByte = ((isOn ? 1 : 0) << 3) | ((isBlue ? 1 : 0) << 2) | ((isClockwise ? 1 : 0) << 1);

        // 血量：2 字节
        int healthHighByte = (health >> 8) & 0xFF; // 高 8 位
        int healthLowByte = health & 0xFF;        // 低 8 位

        // 校验位：状态字节 + 血量高 8 位 + 血量低 8 位
        int checksum = (statusByte + healthHighByte + healthLowByte) & 0xFF;

        // 构建数据帧
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0xAA); // 帧头
        frame.write(statusByte); // 状态字节
        frame.write(healthHighByte); // 血量高 8 位
        frame.write(healthLowByte); // 血量低 8 位
        frame.write(checksum); // 校验位
        frame.write(0x55); // 帧尾

        return frame.toByteArray();
    }

    @SuppressLint("DefaultLocale")
    private String parseFrame(byte[] buffer, int length) {
        // 检查帧长度
        if (length < 6) {  // 帧头（1） + 状态字节（1） + 血量（2） + 校验位（1） + 帧尾（1） = 6
            return "Invalid frame";
        }

        // 检查帧头和帧尾
        if (buffer[0] != (byte) 0xAA || buffer[length - 1] != (byte) 0x55) {
            return "Invalid frame header or footer";
        }

        // 解析状态字节
        int statusByte = buffer[1] & 0xFF;
        boolean isOn = ((statusByte >> 3) & 0x01) == 1;      // 位 3：开启状态
        boolean isBlue = ((statusByte >> 2) & 0x01) == 1;   // 位 2：颜色
        boolean isClockwise = ((statusByte >> 1) & 0x01) == 1; // 位 1：旋转方向

        // 解析血量（2 字节）
        int healthHighByte = buffer[2] & 0xFF;  // 血量高 8 位
        int healthLowByte = buffer[3] & 0xFF;   // 血量低 8 位
        int health = (healthHighByte << 8) | healthLowByte; // 组合为 16 位血量

        // 校验位
        int checksum = buffer[4] & 0xFF;

        // 计算校验位
        int calculatedChecksum = (statusByte + healthHighByte + healthLowByte) & 0xFF;
        if (checksum != calculatedChecksum) {
            return "Checksum mismatch";
        }

        // 返回解析结果
        return String.format("开启状态: %s, 颜色: %s, 旋转方向: %s, 血量: %d",
                isOn ? "开启" : "关闭",
                isBlue ? "蓝色" : "红色",
                isClockwise ? "顺时针" : "逆时针",
                health);
    }

    @Override
    public void parseReceivedData(String data) {
        Log.d(TAG, "解析接收数据: " + data);

        // 假设 data 的格式为 "开启状态: 开启, 颜色: 蓝色, 旋转方向: 顺时针, 血量: 2000"
        String[] parts = data.split(", ");
        if (parts.length < 4) {
            Log.e(TAG, "数据格式不正确");
            return;
        }

        // 解析开启状态
        boolean isOn = parts[0].contains("开启");
        // 解析颜色
        boolean isBlue = parts[1].contains("蓝色");
        // 解析旋转方向
        boolean isClockwise = parts[2].contains("顺时针");
        // 解析血量
        int health = Integer.parseInt(parts[3].split(": ")[1]);

        // 更新 UI 控件
        runOnUiThread(() -> {
            switchSetting1.setChecked(isOn);
            switchSetting2.setChecked(isBlue);
            switchSetting3.setChecked(isClockwise);
            seekBar.setProgress(health);
            etHealthValue.setText(String.valueOf(health));
        });
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
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