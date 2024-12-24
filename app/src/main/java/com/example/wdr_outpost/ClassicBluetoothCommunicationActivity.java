package com.example.wdr_outpost;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.graphics.Color;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class ClassicBluetoothCommunicationActivity extends AppCompatActivity {

    private static final String TAG = "ClassicBluetoothComm"; // 日志标签

    // UI 组件
    private ScrollView scrollView;
    private TextView communicationTextView;
    private EditText inputEditText;
    private Button sendButton;

    // 蓝牙相关变量
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    // 数据接收线程
    private Thread receiveThread;

    // 广播接收器，用于接收配对状态
    private final BroadcastReceiver pairingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (state == BluetoothDevice.BOND_BONDED) {
                    // 配对成功，开始连接
                    Log.d(TAG, "设备配对成功: " + bluetoothDevice.getAddress());
                    connectToDevice();
                } else if (state == BluetoothDevice.BOND_NONE) {
                    // 配对失败
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
        setContentView(R.layout.activity_classic_bluetooth_communication);

        // 初始化 UI 组件
        scrollView = findViewById(R.id.scrollView);
        communicationTextView = findViewById(R.id.communicationTextView);
        inputEditText = findViewById(R.id.inputEditText);
        sendButton = findViewById(R.id.sendButton);

        // 获取 BluetoothManager
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Toast.makeText(this, "无法获取 BluetoothManager", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取 BluetoothAdapter
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "无法获取 BluetoothAdapter", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取设备 MAC 地址
        String deviceMacAddress = getIntent().getStringExtra("deviceMacAddress");
        if (deviceMacAddress != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMacAddress);
            if (bluetoothDevice != null) {
                // 检查设备是否已配对
                if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                    // 已配对，直接连接
                    Log.d(TAG, "设备已配对: " + bluetoothDevice.getAddress());
                    connectToDevice();
                } else {
                    // 未配对，请求配对
                    Log.d(TAG, "设备未配对: " + bluetoothDevice.getAddress());
                    requestPairing();
                }
            } else {
                Toast.makeText(this, "无法获取蓝牙设备", Toast.LENGTH_SHORT).show();
            }
        }

        // 发送按钮点击事件
        sendButton.setOnClickListener(v -> {
            String message = inputEditText.getText().toString();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });

        // 注册配对广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(pairingReceiver, filter);
    }

    // 请求配对
    @SuppressLint("MissingPermission")
    private void requestPairing() {
        try {
            bluetoothDevice.createBond();
            Toast.makeText(this, "请求配对", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "请求配对设备: " + bluetoothDevice.getAddress());
        } catch (Exception e) {
            Log.e(TAG, "配对请求失败: " + e.getMessage(), e); // 使用 Log.e 记录异常
            Toast.makeText(this, "配对请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 连接设备
    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        try {
            // 使用 UUID 连接设备
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            Log.d(TAG, "尝试连接经典蓝牙设备: " + bluetoothDevice.getAddress());
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "经典蓝牙设备连接成功: " + bluetoothDevice.getAddress());

            // 启动数据接收线程
            startReceiveThread();
        } catch (IOException e) {
            Log.e(TAG, "经典蓝牙设备连接失败: " + e.getMessage(), e); // 使用 Log.e 记录异常
            Toast.makeText(this, "连接失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 启动数据接收线程
    private void startReceiveThread() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    // 读取数据
                    bytes = inputStream.read(buffer);
                    String message = new String(buffer, 0, bytes);
                    Log.d(TAG, "接收消息: " + message);

                    // 获取当前时间戳
                    String timeStamp = getCurrentTimeStamp();

                    // 在 UI 线程中更新 TextView
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // 使用 SpannableString 设置接收的文本颜色为蓝色
                        SpannableString spannableString = new SpannableString(timeStamp + "\n [接收] " + message + "\n");
                        spannableString.setSpan(new ForegroundColorSpan(Color.BLUE), 0, spannableString.length(), 0);
                        communicationTextView.append(spannableString);
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN); // 滚动到底部
                    });
                } catch (IOException e) {
                    Log.e(TAG, "数据接收失败: " + e.getMessage());
                    break;
                }
            }
        });
        receiveThread.start();
    }

    // 发送消息
    private void sendMessage(String message) {
        try {
            outputStream.write(message.getBytes());
            // 获取当前时间戳
            String timeStamp = getCurrentTimeStamp();
            // 使用 SpannableString 设置发送的文本颜色为绿色
            SpannableString spannableString = new SpannableString(timeStamp + "\n [发送] " + message + "\n");
            spannableString.setSpan(new ForegroundColorSpan(Color.GREEN), 0, spannableString.length(), 0);
            communicationTextView.append(spannableString);
            Log.d(TAG, "发送消息: " + message);
        } catch (IOException e) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "发送消息失败: " + e.getMessage());
        }
    }

    private String getCurrentTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy 中发生异常: " + e.getMessage(), e); // 使用 Log.e 记录异常
        }
        // 停止数据接收线程
        if (receiveThread != null && receiveThread.isAlive()) {
            receiveThread.interrupt();
        }
    }
}

