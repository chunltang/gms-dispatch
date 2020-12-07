package com.baseboot.common.utils;

import java.util.Random;

public class RandomUtil {


    public static String randString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public static int randInt(int start, int end) {//双开区间
        return new Random().nextInt(end - start + 1) + start;
    }

    public static void main(String[] args) {
        System.out.println(randString(5));
    }
}
