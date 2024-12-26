// BluetoothCommunicator.java
package com.example.wdr_outpost.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothCommunicator {
    private static final String TAG = "BluetoothComm";
    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;
    private final BluetoothCallback callback;

    public BluetoothCommunicator(BluetoothCallback callback) {
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID);
            Log.d(TAG, "尝试连接: " + device.getAddress());
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            callback.onConnected();
            startReceiving();
        } catch (IOException e) {
            Log.e(TAG, "连接失败: " + e.getMessage());
            callback.onConnectionFailed("连接失败: " + e.getMessage());
        }
    }

    public void sendData(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        try {
            byte[] frame = buildFrame(isOn, isBlue, isClockwise, health);
            outputStream.write(frame);
            Log.d(TAG, "数据已发送: " + bytesToHex(frame));
        } catch (IOException e) {
            Log.e(TAG, "发送失败: " + e.getMessage());
            callback.onError("发送失败: " + e.getMessage());
        }
    }

    private void startReceiving() {
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = parseFrame(buffer, bytes);
                        callback.onDataReceived(message);
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        Log.e(TAG, "接收错误: " + e.getMessage());
                        callback.onError("接收错误: " + e.getMessage());
                    }
                    break;
                }
            }
        });
        receiveThread.start();
    }

    private byte[] buildFrame(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        int statusByte = ((isOn ? 1 : 0) << 3) | ((isBlue ? 1 : 0) << 2) | ((isClockwise ? 1 : 0) << 1);
        int healthHighByte = (health >> 8) & 0xFF;
        int healthLowByte = health & 0xFF;
        int checksum = (statusByte + healthHighByte + healthLowByte) & 0xFF;

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0xAA); // Header
        frame.write(statusByte);
        frame.write(healthHighByte);
        frame.write(healthLowByte);
        frame.write(checksum);
        frame.write(0x55); // Footer
        return frame.toByteArray();
    }

    @SuppressLint("DefaultLocale")
    private String parseFrame(byte[] buffer, int length) {
        if (length < 6 || buffer[0] != (byte) 0xAA || buffer[length - 1] != (byte) 0x55) {
            return "无效帧";
        }

        int statusByte = buffer[1] & 0xFF;
        boolean isOn = ((statusByte >> 3) & 0x01) == 1;
        boolean isBlue = ((statusByte >> 2) & 0x01) == 1;
        boolean isClockwise = ((statusByte >> 1) & 0x01) == 1;

        int healthHighByte = buffer[2] & 0xFF;
        int healthLowByte = buffer[3] & 0xFF;
        int health = (healthHighByte << 8) | healthLowByte;

        return String.format("开启状态: %s, 颜色: %s, 旋转方向: %s, 血量: %d",
                isOn ? "开启" : "关闭",
                isBlue ? "蓝色" : "红色",
                isClockwise ? "顺时针" : "逆时针",
                health);
    }

    public void disconnect() {
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "断开连接错误: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    public interface BluetoothCallback {
        void onConnected();
        void onConnectionFailed(String error);
        void onDataReceived(String data);
        void onError(String error);
    }
}