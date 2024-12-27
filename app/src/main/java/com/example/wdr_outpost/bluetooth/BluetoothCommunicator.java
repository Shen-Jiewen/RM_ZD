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
import java.util.Arrays;
import java.util.UUID;

public class BluetoothCommunicator {
    private static final String TAG = "BluetoothComm";
    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;
    private final BluetoothCallback callback;

    private byte[] receiveBuffer = new byte[1024]; // 接收缓冲区
    private int bufferLength = 0; // 缓冲区中有效数据的长度

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
                        processReceivedData(buffer, bytes); // 处理接收到的数据
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

    /**
     * 处理接收到的数据，解决粘包问题
     *
     * @param data   新接收到的数据
     * @param length 新接收到的数据长度
     */
    private void processReceivedData(byte[] data, int length) {
        // 将新数据追加到缓冲区
        if (bufferLength + length > receiveBuffer.length) {
            // 如果缓冲区不够大，扩容缓冲区
            receiveBuffer = Arrays.copyOf(receiveBuffer, bufferLength + length);
        }
        System.arraycopy(data, 0, receiveBuffer, bufferLength, length);
        bufferLength += length;

        // 解析缓冲区中的数据帧
        int startIndex = 0;
        while (startIndex + 6 <= bufferLength) { // 至少需要 6 个字节才能构成一帧
            // 查找帧头 (0xAA)
            if (receiveBuffer[startIndex] != (byte) 0xAA) {
                startIndex++;
                continue;
            }

            // 查找帧尾 (0x55)
            int endIndex = startIndex + 1;
            while (endIndex < bufferLength && receiveBuffer[endIndex] != (byte) 0x55) {
                endIndex++;
            }

            // 如果找到完整的帧
            if (endIndex < bufferLength && receiveBuffer[endIndex] == (byte) 0x55) {
                int frameLength = endIndex - startIndex + 1;
                if (frameLength >= 6) { // 确保帧长度至少为 6
                    byte[] frame = Arrays.copyOfRange(receiveBuffer, startIndex, startIndex + frameLength);
                    String result = parseFrame(frame, frameLength);
                    Log.d(TAG, "解析结果: " + result);
                    callback.onDataReceived(result);

                    // 移动缓冲区，移除已处理的数据
                    System.arraycopy(receiveBuffer, startIndex + frameLength, receiveBuffer, 0, bufferLength - (startIndex + frameLength));
                    bufferLength -= (startIndex + frameLength);
                    startIndex = 0; // 重置起始位置
                } else {
                    startIndex++;
                }
            } else {
                break; // 未找到完整的帧，退出循环
            }
        }
    }

    private byte[] buildFrame(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        int statusByte = ((isOn ? 1 : 0) << 3) | ((isBlue ? 1 : 0) << 2) | ((isClockwise ? 1 : 0) << 1);
        int healthHighByte = (health >> 8) & 0xFF;
        int healthLowByte = health & 0xFF;
        int checksum = (statusByte + healthHighByte + healthLowByte) & 0xFF;

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0xAA);
        frame.write(statusByte);
        frame.write(healthHighByte);
        frame.write(healthLowByte);
        frame.write(checksum);
        frame.write(0x55);
        return frame.toByteArray();
    }

    @SuppressLint("DefaultLocale")
    private String parseFrame(byte[] buffer, int length) {
        // 将 buffer 内容以十六进制格式输出到日志
        StringBuilder hexBuffer = new StringBuilder();
        for (int i = 0; i < length; i++) {
            hexBuffer.append(String.format("%02X ", buffer[i])); // 每个字节转换为两位十六进制，用空格分隔
        }
        Log.d(TAG, "接收到数据 (Hex): " + hexBuffer.toString().trim());

        // 检查帧的有效性
        if (length < 6 || buffer[0] != (byte) 0xAA || buffer[length - 1] != (byte) 0x55) {
            return "无效帧";
        }

        // 解析状态字节
        int statusByte = buffer[1] & 0xFF;
        boolean isOn = ((statusByte >> 3) & 0x01) == 1;
        boolean isBlue = ((statusByte >> 2) & 0x01) == 1;
        boolean isClockwise = ((statusByte >> 1) & 0x01) == 1;

        // 解析血量
        int healthHighByte = buffer[2] & 0xFF;
        int healthLowByte = buffer[3] & 0xFF;
        int health = (healthHighByte << 8) | healthLowByte;

        // 返回解析结果
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