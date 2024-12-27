package com.example.wdr_outpost;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainJaven"; // 日志标签

    private static final int REQUEST_CODE_BLUETOOTH_CONNECT = 1001;

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<String> deviceList = new ArrayList<>();
    private BluetoothDeviceAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
                    // 权限已授予，开始扫描设备
                    scanDevices();
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
                    // 检查是否具有 BLUETOOTH_CONNECT 权限
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        @SuppressLint("MissingPermission") String deviceName = device.getName();
                        // 移除对设备名称的过滤，显示所有设备
                        if (deviceName != null) {
                            @SuppressLint("MissingPermission") String deviceInfo = device.getName() + " [Classic]\n" + device.getAddress();
                            if (!deviceList.contains(deviceInfo)) {
                                deviceList.add(deviceInfo);
                                handler.post(() -> adapter.notifyItemInserted(deviceList.size() - 1)); // 通知插入新项
                            }
                        }
                    } else {
                        Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限，无法获取设备名称");
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

        // 扫描按钮点击事件
        scanButton.setOnClickListener(v -> {
            if (bluetoothAdapter.isEnabled()) {
                // 检查权限
                if (checkPermissions()) {
                    scanDevices();
                }
            } else {
                // 如果蓝牙未启用，请求用户启用蓝牙
                requestEnableBluetooth();
            }
        });
    }

    // 检查并请求权限
    @SuppressLint("ObsoleteSdkInt")
    private boolean checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissionLauncher.launch(permissions);
            return false;
        } else {
            return true;
        }
    }

    // 请求启用蓝牙
    @SuppressLint("ObsoleteSdkInt")
    private void requestEnableBluetooth() {
        // 检查 Android 版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本，需要 BLUETOOTH_CONNECT 权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // 请求 BLUETOOTH_CONNECT 权限
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_CONNECT);
                return; // 等待权限请求结果
            }
        }

        // 权限已授予或不需要权限，启动蓝牙启用请求
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，启动蓝牙启用请求
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                enableBluetoothLauncher.launch(enableBtIntent);
            } else {
                Toast.makeText(this, "BLUETOOTH_CONNECT 权限被拒绝，无法启用蓝牙", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 扫描蓝牙设备
    @SuppressLint("MissingPermission")
    private void scanDevices() {
        // 清空设备列表
        int previousSize = deviceList.size();
        deviceList.clear();

        // 通知适配器删除所有项
        if (previousSize > 0) {
            handler.post(() -> adapter.notifyItemRangeRemoved(0, previousSize));
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
        new Thread(() -> {
            // 检查是否具有 BLUETOOTH_CONNECT 权限
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMacAddress);
                if (device != null) {
                    // 检查设备是否已配对
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        // 只有设备名称为 OUTPOST 时才跳转
                        if (device.getName() != null && device.getName().equals("OUTPOST")) {
                            Intent intent = new Intent(MainActivity.this, OutpostDeviceActivity.class);
                            intent.putExtra("deviceMacAddress", deviceMacAddress);
                            startActivity(intent);
                        } else {
                            handler.post(() -> Toast.makeText(MainActivity.this, "设备不支持跳转", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        // 未配对，请求配对
                        requestPairing(device);
                    }
                } else {
                    handler.post(() -> Toast.makeText(MainActivity.this, "无法连接经典蓝牙设备", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限，无法连接设备");
            }
        }).start();
    }

    // 请求配对
    @SuppressLint("MissingPermission")
    private void requestPairing(BluetoothDevice device) {
        new Thread(() -> {
            try {
                device.createBond();
                handler.post(() -> Toast.makeText(MainActivity.this, "请求配对", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "请求配对设备: " + device.getAddress());
            } catch (Exception e) {
                Log.e(TAG, "配对请求失败: " + e.getMessage(), e);
                handler.post(() -> Toast.makeText(MainActivity.this, "配对请求失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
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