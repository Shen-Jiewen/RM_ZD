package com.example.wdr_outpost.protocol;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

public class OutpostProtocol implements DeviceProtocol<OutpostProtocol.OutpostData> {
    // 帧头标识
    private static final byte FRAME_HEADER = (byte) 0xAA;
    // 帧尾标识
    private static final byte FRAME_TAIL = (byte) 0x55;

    // OutpostData 类，表示前哨站的数据
    public static class OutpostData {
        private final boolean isOn;        // 是否开启
        private final boolean isBlue;      // 是否为蓝色
        private final boolean isClockwise; // 是否为顺时针
        private final int health;          // 健康值

        // 构造函数
        public OutpostData(boolean isOn, boolean isBlue, boolean isClockwise, int health) {
            this.isOn = isOn;
            this.isBlue = isBlue;
            this.isClockwise = isClockwise;
            this.health = health;
        }

        // 获取是否开启的状态
        public boolean isOn() { return isOn; }
        // 获取是否为蓝色的状态
        public boolean isBlue() { return isBlue; }
        // 获取是否为顺时针的状态
        public boolean isClockwise() { return isClockwise; }
        // 获取健康值
        public int getHealth() { return health; }
    }

    /**
     * 将 OutpostData 对象编码为字节数组。
     *
     * @param data 要编码的 OutpostData 对象
     * @return 编码后的字节数组
     */
    @Override
    public byte[] encodeData(OutpostData data) {
        // 将状态信息编码为一个字节
        int statusByte = ((data.isOn() ? 1 : 0) << 3) |
                ((data.isBlue() ? 1 : 0) << 2) |
                ((data.isClockwise() ? 1 : 0) << 1);

        // 处理健康值
        int health = data.getHealth();
        if (health == 5100) {
            health = 0xFFFF; // 设置为无限
        }

        // 将健康值分为高字节和低字节
        int healthHighByte = (health >> 8) & 0xFF;
        ByteArrayOutputStream frame = getByteArrayOutputStream(health, statusByte, healthHighByte);
        return frame.toByteArray();       // 返回帧数据的字节数组
    }

    @NonNull
    private static ByteArrayOutputStream getByteArrayOutputStream(int health, int statusByte, int healthHighByte) {
        int healthLowByte = health & 0xFF;

        // 计算校验和
        int checksum = (statusByte + healthHighByte + healthLowByte) & 0xFF;

        // 构建帧数据
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(FRAME_HEADER);        // 写入帧头
        frame.write(statusByte);          // 写入状态字节
        frame.write(healthHighByte);      // 写入健康值高字节
        frame.write(healthLowByte);       // 写入健康值低字节
        frame.write(checksum);            // 写入校验和
        frame.write(FRAME_TAIL);          // 写入帧尾
        return frame;
    }

    /**
     * 将字节数组解码为 OutpostData 对象。
     *
     * @param data 要解码的字节数组
     * @return 解码后的 OutpostData 对象
     * @throws IllegalArgumentException 如果帧格式无效
     */
    @Override
    public OutpostData decodeData(byte[] data) {
        // 检查帧格式是否有效
        if (data == null || data.length < 6 || data[0] != FRAME_HEADER || data[data.length - 1] != FRAME_TAIL) {
            throw new IllegalArgumentException("Invalid frame format");
        }

        // 解码状态字节
        int statusByte = data[1] & 0xFF;
        boolean isOn = ((statusByte >> 3) & 0x01) == 1;
        boolean isBlue = ((statusByte >> 2) & 0x01) == 1;
        boolean isClockwise = ((statusByte >> 1) & 0x01) == 1;

        // 解码健康值
        int healthHighByte = data[2] & 0xFF;
        int healthLowByte = data[3] & 0xFF;
        int health = (healthHighByte << 8) | healthLowByte;

        // 返回解码后的 OutpostData 对象
        return new OutpostData(isOn, isBlue, isClockwise, health);
    }
}