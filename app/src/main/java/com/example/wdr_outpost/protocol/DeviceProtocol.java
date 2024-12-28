package com.example.wdr_outpost.protocol;

public interface DeviceProtocol<T> {
    /**
     * 将数据对象编码为字节数组。
     *
     * @param data 要编码的数据对象
     * @return 编码后的字节数组
     */
    byte[] encodeData(T data);

    /**
     * 将字节数组解码为数据对象。
     *
     * @param data 要解码的字节数组
     * @return 解码后的数据对象
     */
    T decodeData(byte[] data);
}