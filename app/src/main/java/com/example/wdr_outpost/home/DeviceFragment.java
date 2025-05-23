package com.example.wdr_outpost.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wdr_outpost.OutpostDeviceActivity;
import com.example.wdr_outpost.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeviceFragment extends Fragment {

    private static final String TAG = "DeviceFragment";
    private static final int REQUEST_CODE_BLUETOOTH_CONNECT = 1001;

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<String> deviceList = new ArrayList<>();
    private BluetoothDeviceAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isReceiverRegistered = false;

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = result.entrySet().stream().allMatch(Map.Entry::getValue);
                if (allGranted) {
                    Toast.makeText(getContext(), "权限已授予", Toast.LENGTH_SHORT).show();
                    scanDevices();
                } else {
                    Toast.makeText(getContext(), "权限被拒绝，无法扫描设备", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                getActivity();
                if (result.getResultCode() == Activity.RESULT_OK) {
                    scanDevices();
                } else {
                    Toast.makeText(getContext(), "用户拒绝启用蓝牙", Toast.LENGTH_SHORT).show();
                }
            });

    private final BroadcastReceiver classicBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    @SuppressLint("MissingPermission") String deviceName = device.getName();
                    if (deviceName != null) {
                        @SuppressLint("MissingPermission") String deviceInfo = device.getName() + " [Classic]\n" + device.getAddress();
                        handler.post(() -> {
                            if (!deviceList.contains(deviceInfo)) {
                                deviceList.add(deviceInfo);
                                adapter.notifyItemInserted(deviceList.size() - 1);
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限，无法获取设备名称");
                }
            }
        }
    };

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        // 恢复设备列表
        if (savedInstanceState != null) {
            deviceList.clear();
            deviceList.addAll(savedInstanceState.getStringArrayList("deviceList"));
        }

        BluetoothManager bluetoothManager = (BluetoothManager) requireActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null || bluetoothManager.getAdapter() == null) {
            Toast.makeText(getContext(), "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return view;
        }

        bluetoothAdapter = bluetoothManager.getAdapter();

        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        Button scanButton = view.findViewById(R.id.scanButton);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new BluetoothDeviceAdapter(deviceList, deviceInfo -> {
            String[] parts = deviceInfo.split("\n");
            String deviceName = parts[0].split(" ")[0];
            String deviceMacAddress = parts[1];
            String deviceType = parts[0].split(" ")[1];

            Log.d(TAG, "点击设备: " + deviceName + ", MAC: " + deviceMacAddress + ", 类型: " + deviceType);

            if (deviceType.equals("[Classic]")) {
                connectClassicBluetoothDevice(deviceMacAddress);
            } else {
                Toast.makeText(getContext(), "不支持 BLE 设备", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setAdapter(adapter);

        scanButton.setOnClickListener(v -> {
            if (bluetoothAdapter.isEnabled()) {
                if (checkPermissions()) {
                    scanDevices();
                }
            } else {
                requestEnableBluetooth();
            }
        });

        // 通知适配器更新
        if (savedInstanceState != null) {
            adapter.notifyDataSetChanged();
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList("deviceList", deviceList);
    }

    @SuppressLint({"ObsoleteSdkInt", "UseRequireInsteadOfGet"})
    private boolean checkPermissions() {
        String[] permissions = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION} :
                new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION};

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
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

    @SuppressLint({"ObsoleteSdkInt", "UseRequireInsteadOfGet"})
    private void requestEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_CODE_BLUETOOTH_CONNECT);
            return;
        }

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        enableBluetoothLauncher.launch(enableBtIntent);
    }

    @SuppressLint("MissingPermission")
    private void scanDevices() {
        int previousSize = deviceList.size();
        deviceList.clear();
        if (previousSize > 0) {
            handler.post(() -> adapter.notifyItemRangeRemoved(0, previousSize));
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(classicBluetoothReceiver, filter);
        isReceiverRegistered = true;

        bluetoothAdapter.startDiscovery();
    }

    @SuppressLint("MissingPermission")
    private void connectClassicBluetoothDevice(String deviceMacAddress) {
        new Thread(() -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceMacAddress);
                if (device != null) {
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        if (device.getName() != null && device.getName().equals("OUTPOST")) {
                            Intent intent = new Intent(getActivity(), OutpostDeviceActivity.class);
                            intent.putExtra("deviceMacAddress", deviceMacAddress);
                            startActivity(intent);
                        } else {
                            handler.post(() -> Toast.makeText(getContext(), "设备不支持跳转", Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        requestPairing(device);
                    }
                } else {
                    handler.post(() -> Toast.makeText(getContext(), "无法连接经典蓝牙设备", Toast.LENGTH_SHORT).show());
                }
            } else {
                Log.e(TAG, "缺少 BLUETOOTH_CONNECT 权限，无法连接设备");
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private void requestPairing(BluetoothDevice device) {
        new Thread(() -> {
            try {
                device.createBond();
                handler.post(() -> Toast.makeText(getContext(), "请求配对", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "请求配对设备: " + device.getAddress());
            } catch (Exception e) {
                Log.e(TAG, "配对请求失败: " + e.getMessage());
                handler.post(() -> Toast.makeText(getContext(), "配对请求失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @SuppressLint({"MissingPermission", "UseRequireInsteadOfGet"})
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery();
        } else {
            Log.e(TAG, "缺少 BLUETOOTH_SCAN 权限，无法停止蓝牙扫描");
        }

        if (isReceiverRegistered) {
            requireActivity().unregisterReceiver(classicBluetoothReceiver);
            isReceiverRegistered = false;
        }
    }

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
            CardView cardView;
            TextView deviceName;
            TextView deviceMacAddress;
            TextView deviceType;

            ViewHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardView);
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