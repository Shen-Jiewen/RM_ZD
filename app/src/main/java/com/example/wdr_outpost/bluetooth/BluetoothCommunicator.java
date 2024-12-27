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

    /**
     * 连接到指定的蓝牙设备。
     *
     * @param device 要连接的蓝牙设备。
     */
    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
        if (device == null) {
            callback.onConnectionFailed("设备为空，无法连接");
            return;
        }

        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(DEFAULT_UUID);
            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
            callback.onConnected();
            startReceiving();
        } catch (IOException e) {
            callback.onConnectionFailed("连接失败: " + e.getMessage());
        }
    }

    /**
     * 发送数据到连接的蓝牙设备。
     *
     * @param isOn        设备是否开启。
     * @param isBlue      设备是否为蓝色。
     * @param isClockwise 设备是否顺时针旋转。
     * @param health      设备的血量。
     */
    public void sendData(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
        if (outputStream == null) {
            callback.onError("正在连接...");
            return;
        }

        try {
            byte[] frame = buildFrame(isOn, isBlue, isClockwise, health);
            outputStream.write(frame);
        } catch (IOException e) {
            callback.onError("发送失败: " + e.getMessage());
        }
    }

    /**
     * 启动接收数据的线程。
     */
    private void startReceiving() {
        if (inputStream == null) {
            callback.onError("输入流为空，无法启动接收线程");
            return;
        }

        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        processReceivedData(buffer, bytes);
                    }
                } catch (IOException e) {
                    if (!Thread.currentThread().isInterrupted()) {
                        callback.onError("接收错误: " + e.getMessage());
                    }
                    break;
                }
            }
        });
        receiveThread.start();
    }

    /**
     * 处理接收到的数据，解决粘包问题。
     *
     * @param data   新接收到的数据。
     * @param length 新接收到的数据长度。
     */
    private void processReceivedData(byte[] data, int length) {
        if (data == null || length <= 0) {
            return;
        }

        // 将新数据追加到缓冲区
        if (bufferLength + length > receiveBuffer.length) {
            receiveBuffer = Arrays.copyOf(receiveBuffer, bufferLength + length);
        }
        System.arraycopy(data, 0, receiveBuffer, bufferLength, length);
        bufferLength += length;

        // 解析缓冲区中的数据帧
        int startIndex = 0;
        while (startIndex + 6 <= bufferLength) {
            if (receiveBuffer[startIndex] != (byte) 0xAA) {
                startIndex++;
                continue;
            }

            int endIndex = startIndex + 1;
            while (endIndex < bufferLength && receiveBuffer[endIndex] != (byte) 0x55) {
                endIndex++;
            }

            if (endIndex < bufferLength && receiveBuffer[endIndex] == (byte) 0x55) {
                int frameLength = endIndex - startIndex + 1;
                if (frameLength >= 6) {
                    byte[] frame = Arrays.copyOfRange(receiveBuffer, startIndex, startIndex + frameLength);
                    String result = parseFrame(frame, frameLength);
                    callback.onDataReceived(result);

                    System.arraycopy(receiveBuffer, startIndex + frameLength, receiveBuffer, 0, bufferLength - (startIndex + frameLength));
                    bufferLength -= (startIndex + frameLength);
                    startIndex = 0;
                } else {
                    startIndex++;
                }
            } else {
                break;
            }
        }
    }

    /**
     * 构建要发送的数据帧。
     *
     * @param isOn        设备是否开启。
     * @param isBlue      设备是否为蓝色。
     * @param isClockwise 设备是否顺时针旋转。
     * @param health      设备的血量。
     * @return 构建好的数据帧。
     */
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

    /**
     * 解析接收到的数据帧。
     *
     * @param buffer 接收到的数据帧。
     * @param length 数据帧的长度。
     * @return 解析后的结果字符串。
     */
    private String parseFrame(byte[] buffer, int length) {
        if (buffer == null || length <= 0) {
            return "无效帧";
        }

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

    /**
     * 断开蓝牙连接。
     */
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

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 要转换的字节数组。
     * @return 转换后的十六进制字符串。
     */
    private String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "字节数组为空";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }

    /**
     * 蓝牙通信回调接口。
     */
    public interface BluetoothCallback {
        void onConnected();
        void onConnectionFailed(String error);
        void onDataReceived(String data);
        void onError(String error);
    }
}