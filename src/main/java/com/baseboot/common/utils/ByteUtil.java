package com.baseboot.common.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ByteUtil {

    public static long bytes2ShortBigEndian(byte[] bytes) {
        ByteBuffer len2Buffer = ByteBuffer.allocate(2);
        len2Buffer.order(ByteOrder.LITTLE_ENDIAN);
        len2Buffer.put(bytes, 0, bytes.length);
        len2Buffer.flip();
        return len2Buffer.getShort();
    }

    public static int bytes2IntBigEndian(byte[] bytes) {
        ByteBuffer len4Buffer = ByteBuffer.allocate(4);
        len4Buffer.order(ByteOrder.LITTLE_ENDIAN);
        len4Buffer.put(bytes, 0, bytes.length);
        len4Buffer.flip();
        return len4Buffer.getInt();
    }

    public static float bytes2FloatBigEndian(byte[] bytes) {
        ByteBuffer len4Buffer = ByteBuffer.allocate(4);
        len4Buffer.order(ByteOrder.LITTLE_ENDIAN);
        len4Buffer.put(bytes, 0, bytes.length);
        len4Buffer.flip();
        return len4Buffer.getFloat();
    }


    public static double bytes2DoubleBigEndian(byte[] bytes) {
        ByteBuffer len8Buffer = ByteBuffer.allocate(8);
        len8Buffer.order(ByteOrder.LITTLE_ENDIAN);
        len8Buffer.put(bytes, 0, bytes.length);
        len8Buffer.flip();
        return len8Buffer.getDouble();
    }


    public static int bytesToIntLittle(byte[] bytes) {
        int result = 0;
        if (bytes.length == 4) {
            int a = (bytes[0] & 0xff) << 0;
            int b = (bytes[1] & 0xff) << 8;
            int c = (bytes[2] & 0xff) << 16;
            int d = (bytes[3] & 0xff) << 24;
            result = a | b | c | d;
        }
        return result;
    }

    /**
     * byte数组转int
     * */
    public static int bytes2Int(byte[] bytes) {
        int result = 0;
        //将每个byte依次搬运到int相应的位置
        result = bytes[0] & 0xff;
        result = result << 8 | bytes[1] & 0xff;
        result = result << 8 | bytes[2] & 0xff;
        result = result << 8 | bytes[3] & 0xff;
        return result;
    }

    /**
     * 整形int转byte数组
     * */
    public static byte[] int2Bytes(int num) {
        byte[] bytes = new byte[4];
        //通过移位运算，截取低8位的方式，将int保存到byte数组
        bytes[0] = (byte)(num >>> 24);
        bytes[1] = (byte)(num >>> 16);
        bytes[2] = (byte)(num >>> 8);
        bytes[3] = (byte)num;
        return bytes;
    }

    /**
     * byte数组分段拷贝
     * */
    public static void byteRangeCopy(byte[] origin,byte[] newBytes,int startPos){
        if (startPos >= 0) System.arraycopy(newBytes, 0, origin, startPos, newBytes.length);
    }


    /**
     * byte数组转int,大端
     *
     * @param bytes
     * @return
     */
    public static int bytesToIntBig(byte[] bytes) {
        int result = 0;
        if (bytes.length == 4) {
            int a = (bytes[0] & 0xff) << 24;
            int b = (bytes[1] & 0xff) << 16;
            int c = (bytes[2] & 0xff) << 8;
            int d = (bytes[3] & 0xff) << 0;
            result = a | b | c | d;
        }
        return result;
    }

    public static byte[][] convert2ByteArr(byte[] bytes, int len) {
        int index = 0;
        int num = bytes.length / len + 1;
        byte[][] b2 = new byte[num][4];
        for (int i = 0; i < num; i++) {
            int k = len * (index + 1);
            byte[] copy = Arrays.copyOfRange(bytes, index * len, k > bytes.length ? bytes.length : k);
            b2[index] = copy;
            index++;
        }
        return b2;
    }
}
