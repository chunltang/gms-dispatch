package com.baseboot.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

@Slf4j
public class DateUtil {

    public static final String FULL_TIME_PATTERN = "yyyyMMddHHmmss";

    public static final String FULL_TIME_SPLIT_PATTERN = "yyyy-MM-dd HH:mm:ss";

    public static final String FULL_TIME_SPLIT_MILLIS_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final String CST_TIME_PATTERN = "EEE MMM dd HH:mm:ss zzz yyyy";

    public static String formatFullTime(LocalDateTime localDateTime) {
        return formatFullTime(localDateTime, FULL_TIME_PATTERN);
    }

    public static String formatFullTime(LocalDateTime localDateTime, String pattern) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
        return localDateTime.format(dateTimeFormatter);
    }

    public static String getDateFormat(Date date, String dateFormatType) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(dateFormatType, Locale.CHINA);
        return simpleDateFormat.format(date);
    }

    public static String formatCSTTime(String date, String format) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(CST_TIME_PATTERN, Locale.US);
        Date usDate = simpleDateFormat.parse(date);
        return DateUtil.getDateFormat(usDate, format);
    }

    public static Date formatStringTime(String date, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format,Locale.CHINA);
        try {
            return dateFormat.parse(date);
        } catch (ParseException e) {
            log.error("时间解析异常",e);
        }
        return null;
    }

    public static String formatLongToString(Long date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(FULL_TIME_SPLIT_PATTERN);
        return dateFormat.format(new Date(date));
    }

    public static Date formatLongToDate(Long date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(FULL_TIME_SPLIT_PATTERN);
        try {
            return dateFormat.parse(dateFormat.format(new Date(date)));
        } catch (ParseException e) {
            log.error("时间解析异常",e);
        }
        return null;
    }

    public static String formatInstant(Instant instant, String format) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return localDateTime.format(DateTimeFormatter.ofPattern(format));
    }

    /**
     * 获取当前毫秒时间
     * */
    public static String formatStringFullTime(){
        SimpleDateFormat dateFormat = new SimpleDateFormat(FULL_TIME_SPLIT_MILLIS_PATTERN);
        return dateFormat.format(new Date());
    }
}
