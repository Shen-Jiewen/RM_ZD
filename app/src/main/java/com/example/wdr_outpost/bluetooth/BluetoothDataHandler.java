package com.example.wdr_outpost.bluetooth;

public interface BluetoothDataHandler {
    void sendData(boolean isOn, boolean isBlue, boolean isClockwise, int health);
    void parseReceivedData(String data);
}