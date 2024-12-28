package com.example.wdr_outpost.device;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import com.example.wdr_outpost.protocol.DeviceProtocol;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

public class DeviceManager<T> {
    private static final UUID DEFAULT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final DeviceProtocol<T> protocol;
    private final DeviceCallback<T> callback;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;
    private final byte[] receiveBuffer = new byte[1024];
    private int bufferLength = 0;

    public DeviceManager(DeviceProtocol<T> protocol, DeviceCallback<T> callback) {
        this.protocol = protocol;
        this.callback = callback;
    }

    @SuppressLint("MissingPermission")
    public void connect(BluetoothDevice device) {
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

    public void sendData(T data) {
        if (outputStream == null) {
            callback.onError("连接中...");
            return;
        }

        try {
            byte[] encoded = protocol.encodeData(data);
            outputStream.write(encoded);
        } catch (IOException e) {
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

    private void processReceivedData(byte[] data, int length) {
        System.arraycopy(data, 0, receiveBuffer, bufferLength, length);
        bufferLength += length;

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
                    try {
                        T deviceData = protocol.decodeData(frame);
                        callback.onDataReceived(deviceData);
                    } catch (IllegalArgumentException e) {
                        callback.onError("接收到无效帧");
                    }

                    System.arraycopy(receiveBuffer, startIndex + frameLength,
                            receiveBuffer, 0, bufferLength - (startIndex + frameLength));
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

    public void disconnect() {
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            callback.onError("断开连接错误: " + e.getMessage());
        }
    }

    public interface DeviceCallback<T> {
        void onConnected();
        void onConnectionFailed(String error);
        void onDataReceived(T data);
        void onError(String error);
    }
}