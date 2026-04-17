package cn.pandora.common.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期时间工具类
 */
public final class DateUtils {

    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_COMPACT = "yyyyMMddHHmmss";

    public static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    public static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern(PATTERN_DATE);
    public static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern(PATTERN_TIME);
    public static final DateTimeFormatter FMT_COMPACT = DateTimeFormatter.ofPattern(PATTERN_COMPACT);

    private DateUtils() {
    }

    public static String now() {
        return LocalDateTime.now().format(FMT_DATETIME);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(FMT_DATETIME);
    }

    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime == null ? null : dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    public static String format(LocalDate date) {
        return date == null ? null : date.format(FMT_DATE);
    }

    public static LocalDateTime parse(String dateTimeStr) {
        return LocalDateTime.parse(dateTimeStr, FMT_DATETIME);
    }

    public static LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, FMT_DATE);
    }

    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDateTime fromEpochMilli(long epochMilli) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    public static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    public static long betweenDays(LocalDate start, LocalDate end) {
        return Duration.between(start.atStartOfDay(), end.atStartOfDay()).toDays();
    }
}
