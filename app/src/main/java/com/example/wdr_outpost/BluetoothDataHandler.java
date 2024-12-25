package com.example.wdr_outpost;

public interface BluetoothDataHandler {
    void sendData(String data);
    void parseReceivedData(String data);
}
