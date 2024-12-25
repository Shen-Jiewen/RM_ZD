package com.example.wdr_outpost;

public interface BluetoothDataHandler {
    void sendData(boolean isOn, boolean isBlue, boolean isClockwise, int health);
    void parseReceivedData(String data);
}
