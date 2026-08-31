package com.github.henc.integrateboot.base.util;

import com.github.henc.integrateboot.base.datetime.DateTimeRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Date/time toolkit with one opinionated entry point: {@link #getCurrentDateTime()}
 * returns the <em>served</em> current time, taken from the best available time source
 * (Redis, the database, or — as the always-working fallback — the local system clock).
 * The rest of the class is a plain-Java collection of the usual parsing, formatting,
 * arithmetic and truncation helpers, usable without any framework.
 *
 * <p><b>Where the current time comes from.</b> Sources implement
 * {@link com.github.henc.integrateboot.base.datetime.DateTimeService} and are managed by
 * {@link DateTimeRegistry}: {@code integrate-boot-redis} contributes a Redis source
 * (Redis {@code TIME}), {@code integrate-boot-data} a database source
 * ({@code select now()}); the system clock is built in as the last resort. Whichever
 * module is on the classpath registers its source automatically — with none of them,
 * {@link #getCurrentDateTime()} simply answers from the system clock. A source that is
 * down is skipped, so the method never throws and never returns {@code null}. Prefer a
 * specific source with {@code integrate-boot.datetime.prefer} (see the module READMEs)
 * or {@link DateTimeRegistry#setPreferredType(String)}.
 *
 * <p>All conversions use the JVM's default zone, and formatting is not thread-safe by
 * itself, so each call creates its own {@link SimpleDateFormat}.
 */
public final class DateUtils {

    private DateUtils() {
    }

    /** Pattern: date only, e.g. {@code 2024-03-05}. */
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    /** Pattern: month and day only, e.g. {@code 03-05}. */
    public static final String DATE_FORMAT_SHORT = "MM-dd";
    /** Pattern: date and time, e.g. {@code 2024-03-05 14:30:09}. */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    /** Pattern: time only, e.g. {@code 14:30:09}. */
    public static final String TIME_FORMAT = "HH:mm:ss";
    /** Pattern: year and month, e.g. {@code 2024-03}. */
    public static final String DATE_MONTH_FORMAT = "yyyy-MM";
    /** Pattern: year only, e.g. {@code 2024}. */
    public static final String DATE_YEAR_FORMAT = "yyyy";
    /** Pattern: {@link Date#toString()}'s format. */
    public static final String DEFAULT_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";
    /** Pattern: date and hour, e.g. {@code 2024-03-05 14}. */
    public static final String DATE_TIME_HOUR_FORMAT = "yyyy-MM-dd HH";
    /** Pattern: date, hour and minute, e.g. {@code 2024-03-05 14:30}. */
    public static final String DATE_TIME_MIN_FORMAT = "yyyy-MM-dd HH:mm";

    // ---------------------------------------------------------------------
    // Current time
    // ---------------------------------------------------------------------

    /**
     * The current time from the best available time source (see the class javadoc).
     * Reads are cheap: when the usable source allows it, its offset from the local
     * clock is cached and refreshed in the background instead of fetched per call.
     *
     * @return the current time from the best available source
     */
    public static Date getCurrentDateTime() {
        return DateTimeRegistry.getCurrentDateTime();
    }

    /**
     * Like {@link #getCurrentDateTime()}, but guaranteed cheap for high-frequency
     * callers: even when the usable source must be called directly (e.g. the database),
     * it is consulted at most once per check interval and the local clock interpolates
     * in between.
     *
     * @return the current time, interpolated from a cached offset where possible
     */
    public static Date getCurrentDateTimeSimpleInterval() {
        return DateTimeRegistry.getCurrentDateTimeSimpleInterval();
    }

    /**
     * The current time from the local system clock, ignoring every time source.
     *
     * @return the local system time
     */
    public static Date getSystemDateTime() {
        return new Date();
    }

    /**
     * The current time read directly from the usable time source, bypassing the cached
     * offset — exact, at the cost of one remote call.
     *
     * @return the current time read directly from the usable source
     */
    public static Date getDateFromDateTimeService() {
        return DateTimeRegistry.getDateFromService();
    }

    // ---------------------------------------------------------------------
    // Parsing / formatting
    // ---------------------------------------------------------------------

    /**
     * Parses a date string with the given pattern.
     *
     * @return the parsed time, or {@code null} if the input does not match the pattern
     * @param date the time to operate on
     * @param format a {@link SimpleDateFormat} pattern
     */
    public static Date parseDate(String date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.parse(date, new ParsePosition(0));
    }

    /**
     * Formats with {@link #DATE_TIME_FORMAT} ({@code yyyy-MM-dd HH:mm:ss}).
     *
     * @param date the time to format
     * @return the formatted string
     */
    public static String toDateTimeString(Date date) {
        return toDateTimeString(date, DATE_TIME_FORMAT);
    }

    /**
     * Formats with {@link #TIME_FORMAT} ({@code HH:mm:ss}).
     *
     * @param date the time to format
     * @return the formatted string
     */
    public static String toTimeString(Date date) {
        return toDateTimeString(date, TIME_FORMAT);
    }

    /**
     * Formats with {@link #DATE_FORMAT} ({@code yyyy-MM-dd}).
     *
     * @param date the time to format
     * @return the formatted string
     */
    public static String toDateString(Date date) {
        return toDateTimeString(date, DATE_FORMAT);
    }

    /**
     * Formats with an arbitrary {@link SimpleDateFormat} pattern.
     *
     * @param date the time to format
     * @param format a {@link SimpleDateFormat} pattern
     * @return the formatted string
     */
    public static String toDateTimeString(Date date, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(date);
    }

    // ---------------------------------------------------------------------
    // Arithmetic
    // ---------------------------------------------------------------------

    /**
     * Returns the time {@code num} units after (negative: before) the given time.
     *
     * @param calendarType one of the {@link Calendar} field constants
     *                     ({@code Calendar.YEAR}, {@code Calendar.MONTH}, ...)
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addDateTime(Date date, int num, int calendarType) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(calendarType, num);
        return calendar.getTime();
    }

    /**
     * Returns the time {@code num} years after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addYear(Date date, int num) {
        return addDateTime(date, num, Calendar.YEAR);
    }

    /**
     * Returns the time {@code num} months after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addMonth(Date date, int num) {
        return addDateTime(date, num, Calendar.MONTH);
    }

    /**
     * Returns the time {@code num} days after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addDay(Date date, int num) {
        return addDateTime(date, num, Calendar.DATE);
    }

    /**
     * Returns the time {@code num} hours after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addHour(Date date, int num) {
        return addDateTime(date, num, Calendar.HOUR);
    }

    /**
     * Returns the time {@code num} minutes after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addMinute(Date date, int num) {
        return addDateTime(date, num, Calendar.MINUTE);
    }

    /**
     * Returns the time {@code num} seconds after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addSecond(Date date, int num) {
        return addDateTime(date, num, Calendar.SECOND);
    }

    /**
     * Returns the time {@code num} milliseconds after (negative: before) the given time.
     *
     * @param date the time to operate on
     * @param num the number of units to add (negative to go back)
     * @return the shifted time
     */
    public static Date addMillisecond(Date date, int num) {
        return addDateTime(date, num, Calendar.MILLISECOND);
    }

    // ---------------------------------------------------------------------
    // Type conversion
    // ---------------------------------------------------------------------

    /**
     * Converts to {@link LocalDateTime} in the default zone.
     *
     * @param date the time to operate on
     * @return the converted value
     */
    public static LocalDateTime dateToLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Converts from {@link LocalDateTime} in the default zone.
     *
     * @param localDateTime the value to convert
     * @return the converted value
     */
    public static Date localDateTimeToDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Converts to {@link LocalDate} in the default zone.
     *
     * @param date the time to operate on
     * @return the converted value
     */
    public static LocalDate dateToLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Converts from {@link LocalDate}; the time of day is 00:00:00.
     *
     * @param localDate the value to convert
     * @return the converted value
     */
    public static Date localDateToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Converts to {@link LocalTime} in the default zone.
     *
     * @param date the time to operate on
     * @return the converted value
     */
    public static LocalTime dateToLocalTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
    }

    // ---------------------------------------------------------------------
    // Comparison and differences
    // ---------------------------------------------------------------------

    /**
     * Whole days between two dates (calendar days, not 24-hour periods):
     * {@code daysBetweenTwoDate(2024-01-01, 2024-01-03) == 2}.
     * @param beg the earlier time
     * @param end the later time
     * @return the number of whole calendar days ({@code end - beg})
     */
    public static int daysBetweenTwoDate(Date beg, Date end) {
        return Math.toIntExact(ChronoUnit.DAYS.between(dateToLocalDate(beg), dateToLocalDate(end)));
    }

    /**
     * Whether the two times fall on the same calendar date.
     *
     * @param date1 the first time
     * @param date2 the second time
     * @return whether the two times fall on the same calendar date
     */
    public static boolean isSameDate(Date date1, Date date2) {
        return dateToLocalDate(date1).isEqual(dateToLocalDate(date2));
    }

    /**
     * Compares only the time of day (ignoring the date): whether {@code date1} is
     * earlier than {@code date2}.
     * @param date1 the first time
     * @param date2 the second time
     * @return whether {@code date1} is earlier in the day than {@code date2}
     */
    public static boolean beforeOnlyTime(Date date1, Date date2) {
        return dateToLocalTime(date1).isBefore(dateToLocalTime(date2));
    }

    /**
     * Compares only the time of day (ignoring the date): whether {@code date1} is
     * later than {@code date2}.
     * @param date1 the first time
     * @param date2 the second time
     * @return whether {@code date1} is later in the day than {@code date2}
     */
    public static boolean afterOnlyTime(Date date1, Date date2) {
        return dateToLocalTime(date1).isAfter(dateToLocalTime(date2));
    }

    /**
     * Milliseconds between two times ({@code date2 - date1}).
     *
     * @param date1 the first time
     * @param date2 the second time
     * @return the difference in milliseconds ({@code date2 - date1})
     */
    public static long millisecondsOfTwoDate(Date date1, Date date2) {
        return date2.getTime() - date1.getTime();
    }

    /**
     * Seconds between two times, rounded to one decimal place.
     *
     * @param date1 the first time
     * @param date2 the second time
     * @return the difference in seconds, one decimal place
     */
    public static double secondsOfTwoDate(Date date1, Date date2) {
        return scaleDouble(millisecondsOfTwoDate(date1, date2) / 1000d, 1);
    }

    /**
     * Seconds between two times, rounded to a whole number.
     *
     * @param date1 the first time
     * @param date2 the second time
     * @return the difference in whole seconds
     */
    public static int secondsNumOfTwoDate(Date date1, Date date2) {
        return scaleDoubleInt(millisecondsOfTwoDate(date1, date2) / 1000d, 1);
    }

    /**
     * Minutes between two times, rounded to three decimal places.
     *
     * @param date1 the first time
     * @param date2 the second time
     * @return the difference in minutes, three decimal places
     */
    public static double minutesOfTwoDate(Date date1, Date date2) {
        return scaleDouble(millisecondsOfTwoDate(date1, date2) / 60_000d, 3);
    }

    private static double scaleDouble(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static int scaleDoubleInt(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).intValue();
    }

    // ---------------------------------------------------------------------
    // Truncation to period boundaries
    // ---------------------------------------------------------------------

    /**
     * Start of the given day (00:00:00.000).
     *
     * @param date the time to operate on
     * @return the day start (00:00:00.000)
     */
    public static Date getDayBegin(Date date) {
        return localDateTimeToDate(LocalDateTime.of(dateToLocalDate(date), LocalTime.MIN));
    }

    /**
     * End of the given day (23:59:59.999).
     *
     * @param date the time to operate on
     * @return the day end (23:59:59.999)
     */
    public static Date getDayEnd(Date date) {
        return localDateTimeToDate(LocalDateTime.of(dateToLocalDate(date), LocalTime.MAX));
    }

    /**
     * Start of the day's week (Monday 00:00:00).
     *
     * @param date the time to operate on
     * @return the week start (Monday 00:00:00)
     */
    public static Date getWeekBegin(Date date) {
        TemporalField field = WeekFields.of(DayOfWeek.MONDAY, 1).dayOfWeek();
        return localDateToDate(dateToLocalDate(date).with(field, 1));
    }

    /**
     * End of the day's week (Sunday 00:00:00 — see {@link #getWeekEnd(Date, boolean)}).
     *
     * @param date the time to operate on
     * @return the week end
     */
    public static Date getWeekEnd(Date date) {
        return getWeekEnd(date, false);
    }

    /**
     * End of the day's week.
     *
     * @param lastSecond {@code true} for Sunday 23:59:59.999, {@code false} (default)
     *                   for Sunday 00:00:00
     * @param date the time to operate on
     * @return the week end
     */
    public static Date getWeekEnd(Date date, boolean lastSecond) {
        TemporalField field = WeekFields.of(DayOfWeek.MONDAY, 1).dayOfWeek();
        LocalDateTime localDateTime = dateToLocalDateTime(date).with(field, 7);
        if (lastSecond) {
            return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MAX));
        }
        return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MIN));
    }

    /**
     * First day of the day's month (00:00:00).
     *
     * @param date the time to operate on
     * @return the month start (00:00:00)
     */
    public static Date getMonthBegin(Date date) {
        return localDateToDate(dateToLocalDate(date).withDayOfMonth(1));
    }

    /**
     * Last day of the day's month (00:00:00 — see {@link #getMonthEnd(Date, boolean)}).
     *
     * @param date the time to operate on
     * @return the month end
     */
    public static Date getMonthEnd(Date date) {
        return getMonthEnd(date, false);
    }

    /**
     * Last day of the day's month.
     *
     * @param lastSecond {@code true} for 23:59:59.999, {@code false} (default) for 00:00:00
     * @param date the time to operate on
     * @return the month end
     */
    public static Date getMonthEnd(Date date, boolean lastSecond) {
        LocalDateTime localDateTime = dateToLocalDateTime(date).with(TemporalAdjusters.lastDayOfMonth());
        if (lastSecond) {
            return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MAX));
        }
        return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MIN));
    }

    /**
     * First day of the day's quarter (00:00:00).
     *
     * @param date the time to operate on
     * @return the quarter start (00:00:00)
     */
    public static Date getQuarterBegin(Date date) {
        LocalDate localDate = dateToLocalDate(date);
        return localDateToDate(
                localDate
                        .withMonth(localDate.getMonth().firstMonthOfQuarter().getValue())
                        .withDayOfMonth(1));
    }

    /**
     * Last day of the day's quarter (00:00:00 — see {@link #getQuarterEnd(Date, boolean)}).
     *
     * @param date the time to operate on
     * @return the quarter end
     */
    public static Date getQuarterEnd(Date date) {
        return getQuarterEnd(date, false);
    }

    /**
     * Last day of the day's quarter.
     *
     * @param lastSecond {@code true} for 23:59:59.999, {@code false} (default) for 00:00:00
     * @param date the time to operate on
     * @return the quarter end
     */
    public static Date getQuarterEnd(Date date, boolean lastSecond) {
        LocalDateTime localDateTime = dateToLocalDateTime(date);
        localDateTime = localDateTime
                .withMonth(localDateTime.getMonth().firstMonthOfQuarter().getValue() + 2)
                .with(TemporalAdjusters.lastDayOfMonth());
        if (lastSecond) {
            return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MAX));
        }
        return localDateTimeToDate(LocalDateTime.of(localDateTime.toLocalDate(), LocalTime.MIN));
    }

    // ---------------------------------------------------------------------
    // Enumeration between two times
    // ---------------------------------------------------------------------

    /**
     * Whole hours from {@code startTime}'s hour (inclusive) up to {@code endTime}.
     *
     * @param startTime the start of the range (its hour / day / month is included)
     * @param endTime the end of the range, inclusive
     * @return the result
     */
    public static List<Date> listHoursBetweenTime(Date startTime, Date endTime) {
        List<Date> result = new ArrayList<>();
        LocalDateTime startLocalDateTime = dateToLocalDateTime(startTime);
        LocalDateTime endLocalDateTime = dateToLocalDateTime(endTime);
        LocalDateTime localDateTime = LocalDateTime
                .of(startLocalDateTime.toLocalDate(), LocalTime.MIN)
                .withHour(startLocalDateTime.getHour());
        while (!localDateTime.isAfter(endLocalDateTime)) {
            result.add(localDateTimeToDate(localDateTime));
            localDateTime = localDateTime.plusHours(1);
        }
        return result;
    }

    /**
     * Whole days from {@code startTime}'s day (inclusive) up to {@code endTime}.
     *
     * @param startTime the start of the range (its hour / day / month is included)
     * @param endTime the end of the range, inclusive
     * @return the result
     */
    public static List<Date> listDaysBetweenTime(Date startTime, Date endTime) {
        List<Date> result = new ArrayList<>();
        LocalDate startLocalDate = dateToLocalDate(startTime);
        LocalDate endLocalDate = dateToLocalDate(endTime);
        while (!startLocalDate.isAfter(endLocalDate)) {
            result.add(localDateToDate(startLocalDate));
            startLocalDate = startLocalDate.plusDays(1);
        }
        return result;
    }

    /**
     * Months from {@code startTime}'s month (inclusive) up to {@code endTime}, one entry
     * per month. Entries preserve the start date's day-of-month, normalized to midnight.
     * @param startTime the start of the range (its hour / day / month is included)
     * @param endTime the end of the range, inclusive
     * @return the month times, oldest first
     */
    public static List<Date> listMonthsBetweenTime(Date startTime, Date endTime) {
        List<Date> result = new ArrayList<>();
        LocalDate startLocalDate = dateToLocalDate(startTime);
        LocalDate endLocalDate = dateToLocalDate(endTime);
        while (!startLocalDate.isAfter(endLocalDate)) {
            result.add(localDateToDate(startLocalDate));
            startLocalDate = startLocalDate.plusMonths(1);
        }
        return result;
    }

}
