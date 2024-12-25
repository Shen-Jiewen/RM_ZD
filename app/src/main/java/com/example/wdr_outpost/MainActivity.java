package com.example.wdr_outpost;

import android.Manifest;
import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BluetoothDemo"; // 日志标签
    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<String> deviceList = new ArrayList<>();
    private BluetoothDeviceAdapter adapter;

    private final List<String> macAddressList = Arrays.asList(
            "55:78:44:02:6B:DF",
            "90:F0:52:C8:E9:53"
    );

    // Activity Result API 用于请求权限
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                    if (!entry.getValue()) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "权限被拒绝，无法扫描设备", Toast.LENGTH_SHORT).show();
                }
            });

    // Activity Result API 用于请求启用蓝牙
    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // 用户已启用蓝牙，开始扫描
                    scanDevices();
                } else {
                    // 用户拒绝启用蓝牙
                    Toast.makeText(this, "用户拒绝启用蓝牙", Toast.LENGTH_SHORT).show();
                }
            });

    // 广播接收器，用于接收经典蓝牙设备
    private final BroadcastReceiver classicBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    String deviceMacAddress = device.getAddress();
                    // 检查设备 MAC 地址是否在配置列表中
                    if (macAddressList.contains(deviceMacAddress)) {
                        @SuppressLint("MissingPermission") String deviceInfo = device.getName() + " [Classic]\n" + deviceMacAddress;
                        if (!deviceList.contains(deviceInfo)) {
                            deviceList.add(deviceInfo);
                            adapter.notifyItemInserted(deviceList.size() - 1); // 通知插入新项
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置标题栏
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false); // 隐藏默认标题
        }

        // 初始化蓝牙管理器
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null || bluetoothManager.getAdapter() == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取 BluetoothAdapter
        bluetoothAdapter = bluetoothManager.getAdapter();

        // 初始化 UI 组件
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        Button scanButton = findViewById(R.id.scanButton);

        // 设置 RecyclerView 布局管理器
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 初始化适配器
        adapter = new BluetoothDeviceAdapter(deviceList, deviceInfo -> {
            String[] parts = deviceInfo.split("\n");
            String deviceName = parts[0].split(" ")[0];
            String deviceMacAddress = parts[1];
            String deviceType = parts[0].split(" ")[1];

            Log.d(TAG, "点击设备: " + deviceName + ", MAC: " + deviceMacAddress + ", 类型: " + deviceType);

            if (deviceType.equals("[Classic]")) {
                Log.d(TAG, "尝试连接经典蓝牙设备: " + deviceMacAddress);
                connectClassicBluetoothDevice(deviceMacAddress);
            } else {
                Toast.makeText(MainActivity.this, "不支持 BLE 设备", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);

        // 检查并请求蓝牙权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions();
        }

        // 扫描按钮点击事件
        scanButton.setOnClickListener(v -> {
            if (bluetoothAdapter.isEnabled()) {
                scanDevices();
            } else {
                // 如果蓝牙未启用，请求用户启用蓝牙
                requestEnableBluetooth();
            }
        });
    }

    // 请求权限
    private void requestPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        requestPermissionLauncher.launch(permissions);
    }

    // 请求启用蓝牙
    private void requestEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    // 扫描蓝牙设备
    @SuppressLint("MissingPermission")
    private void scanDevices() {
        // 清空设备列表
        int previousSize = deviceList.size();
        deviceList.clear();

        // 通知适配器删除所有项
        if (previousSize > 0) {
            adapter.notifyItemRangeRemoved(0, previousSize);
        }

        // 注册经典蓝牙广播接收器
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(classicBluetoothReceiver, filter);

        // 开始经典蓝牙扫描
        bluetoothAdapter.startDiscovery();
    }

    // 连接经典蓝牙设备
    @SuppressLint("MissingPermission")
    private void connectClassicBluetoothDevice(String deviceMacAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMacAddress);
        if (device != null) {
            // 检查设备是否已配对
            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                // 已配对，直接跳转到通信页面
                Intent intent = new Intent(MainActivity.this, ClassicBluetoothCommunicationActivity.class);
                intent.putExtra("deviceMacAddress", deviceMacAddress);
                startActivity(intent);
            } else {
                // 未配对，请求配对
                requestPairing(device);
            }
        } else {
            Toast.makeText(this, "无法连接经典蓝牙设备", Toast.LENGTH_SHORT).show();
        }
    }

    // 请求配对
    @SuppressLint("MissingPermission")
    private void requestPairing(BluetoothDevice device) {
        try {
            device.createBond();
            Toast.makeText(this, "请求配对", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "请求配对设备: " + device.getAddress());
        } catch (Exception e) {
            Log.e(TAG, "配对请求失败: " + e.getMessage(), e);
            Toast.makeText(this, "配对请求失败", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止经典蓝牙扫描
        bluetoothAdapter.cancelDiscovery();
        // 取消注册经典蓝牙广播接收器
        unregisterReceiver(classicBluetoothReceiver);
    }

    // 适配器类
    private static class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder> {

        private final List<String> deviceList;
        private final OnItemClickListener listener;

        BluetoothDeviceAdapter(List<String> deviceList, OnItemClickListener listener) {
            this.deviceList = deviceList;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bluetooth_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String deviceInfo = deviceList.get(position);
            String[] parts = deviceInfo.split("\n");
            String deviceName = parts[0].split(" ")[0];
            String deviceMacAddress = parts[1];
            String deviceType = parts[0].split(" ")[1];

            holder.deviceName.setText(deviceName);
            holder.deviceMacAddress.setText(deviceMacAddress);
            holder.deviceType.setText(deviceType);

            // 直接设置 OnClickListener
            holder.cardView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(deviceInfo);
                }
            });
        }

        @Override
        public int getItemCount() {
            return deviceList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CardView cardView; // 添加 CardView 引用
            TextView deviceName;
            TextView deviceMacAddress;
            TextView deviceType;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardView); // 初始化 CardView
                deviceName = itemView.findViewById(R.id.deviceName);
                deviceMacAddress = itemView.findViewById(R.id.deviceMacAddress);
                deviceType = itemView.findViewById(R.id.deviceType);
            }
        }

        interface OnItemClickListener {
            void onItemClick(String deviceInfo);
        }
    }
}