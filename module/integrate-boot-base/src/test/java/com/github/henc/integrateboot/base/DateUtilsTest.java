package com.github.henc.integrateboot.base;

import com.github.henc.integrateboot.base.util.DateUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests for the parsing / formatting / arithmetic / truncation helpers. The
 * current-time entry points are only smoke-tested here (they must always answer);
 * their source selection and fallback behavior is covered by
 * {@code DateTimeRegistryTest}.
 */
class DateUtilsTest {

    private static Date date(int year, int month, int day, int hour, int minute, int second) {
        return DateUtils.localDateTimeToDate(LocalDateTime.of(year, month, day, hour, minute, second));
    }

    /** LocalTime.MAX truncates to 23:59:59.999 in a Date — same shape as getDayEnd etc. */
    private static Date dayEnd(int year, int month, int day) {
        return DateUtils.localDateTimeToDate(LocalDateTime.of(year, month, day, 23, 59, 59, 999_000_000));
    }

    @Test
    void parseAndFormatRoundTrip() {
        Date parsed = DateUtils.parseDate("2024-03-05 14:30:09", DateUtils.DATE_TIME_FORMAT);

        assertThat(parsed).isEqualTo(date(2024, 3, 5, 14, 30, 9));
        assertThat(DateUtils.toDateTimeString(parsed)).isEqualTo("2024-03-05 14:30:09");
        assertThat(DateUtils.toDateString(parsed)).isEqualTo("2024-03-05");
        assertThat(DateUtils.toTimeString(parsed)).isEqualTo("14:30:09");
        assertThat(DateUtils.toDateTimeString(parsed, DateUtils.DATE_MONTH_FORMAT)).isEqualTo("2024-03");
    }

    @Test
    void parseDateReturnsNullOnMismatch() {
        assertThat(DateUtils.parseDate("05/03/2024", DateUtils.DATE_FORMAT)).isNull();
        assertThat(DateUtils.parseDate("", DateUtils.DATE_FORMAT)).isNull();
    }

    @Test
    void addMethodsShiftByUnits() {
        Date base = date(2024, 3, 31, 23, 59, 59);

        assertThat(DateUtils.addYear(base, 1)).isEqualTo(date(2025, 3, 31, 23, 59, 59));
        // Calendar arithmetic clamps to the last valid day of the target month.
        assertThat(DateUtils.addMonth(base, 1)).isEqualTo(date(2024, 4, 30, 23, 59, 59));
        assertThat(DateUtils.addDay(base, 1)).isEqualTo(date(2024, 4, 1, 23, 59, 59));
        assertThat(DateUtils.addHour(base, 1)).isEqualTo(date(2024, 4, 1, 0, 59, 59));
        assertThat(DateUtils.addMinute(base, 1)).isEqualTo(date(2024, 4, 1, 0, 0, 59));
        assertThat(DateUtils.addSecond(base, 1)).isEqualTo(date(2024, 4, 1, 0, 0, 0));
        assertThat(DateUtils.addDay(base, -31)).isEqualTo(date(2024, 2, 29, 23, 59, 59));
    }

    @Test
    void conversionsRoundTrip() {
        Date original = date(2024, 6, 15, 10, 20, 30);

        assertThat(DateUtils.dateToLocalDateTime(original))
                .isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 20, 30));
        assertThat(DateUtils.localDateTimeToDate(DateUtils.dateToLocalDateTime(original))).isEqualTo(original);
        assertThat(DateUtils.dateToLocalDate(original)).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(DateUtils.dateToLocalTime(original)).isEqualTo(LocalTime.of(10, 20, 30));
        // LocalDate converts at midnight.
        assertThat(DateUtils.localDateToDate(LocalDate.of(2024, 6, 15))).isEqualTo(date(2024, 6, 15, 0, 0, 0));
    }

    @Test
    void daysBetweenCountsCalendarDaysAcrossMonthsAndYears() {
        assertThat(DateUtils.daysBetweenTwoDate(date(2024, 1, 1, 0, 0, 0), date(2024, 1, 3, 0, 0, 0)))
                .isEqualTo(2);
        // A Jan 6 -> Mar 6 span is 60 days in 2024 (leap year) — whole months included,
        // not just the day-of-month component.
        assertThat(DateUtils.daysBetweenTwoDate(date(2024, 1, 6, 0, 0, 0), date(2024, 3, 6, 0, 0, 0)))
                .isEqualTo(60);
        assertThat(DateUtils.daysBetweenTwoDate(date(2024, 12, 31, 0, 0, 0), date(2025, 1, 1, 0, 0, 0)))
                .isEqualTo(1);
        assertThat(DateUtils.daysBetweenTwoDate(date(2024, 1, 3, 0, 0, 0), date(2024, 1, 1, 0, 0, 0)))
                .isEqualTo(-2);
    }

    @Test
    void comparisons() {
        assertThat(DateUtils.isSameDate(date(2024, 3, 5, 8, 0, 0), date(2024, 3, 5, 22, 0, 0))).isTrue();
        assertThat(DateUtils.isSameDate(date(2024, 3, 5, 8, 0, 0), date(2024, 3, 6, 8, 0, 0))).isFalse();

        Date morning = date(2024, 3, 5, 8, 0, 0);
        Date laterDay = date(2024, 3, 9, 10, 0, 0);
        // Time-of-day only: 08:00 is before 10:00 regardless of the date.
        assertThat(DateUtils.beforeOnlyTime(morning, laterDay)).isTrue();
        assertThat(DateUtils.afterOnlyTime(morning, laterDay)).isFalse();
    }

    @Test
    void differences() {
        Date beg = date(2024, 3, 5, 8, 0, 0);
        Date end = date(2024, 3, 5, 8, 1, 40);

        assertThat(DateUtils.millisecondsOfTwoDate(beg, end)).isEqualTo(100_000L);
        assertThat(DateUtils.secondsOfTwoDate(beg, end)).isEqualTo(100.0);
        assertThat(DateUtils.secondsNumOfTwoDate(beg, end)).isEqualTo(100);
        assertThat(DateUtils.minutesOfTwoDate(beg, end)).isEqualTo(1.667);
    }

    @Test
    void dayBoundaries() {
        Date day = date(2024, 3, 5, 15, 30, 45);

        assertThat(DateUtils.getDayBegin(day)).isEqualTo(date(2024, 3, 5, 0, 0, 0));
        assertThat(DateUtils.getDayEnd(day)).isEqualTo(dayEnd(2024, 3, 5));
    }

    @Test
    void weekBoundariesStartOnMonday() {
        // 2024-01-04 is a Thursday.
        Date thursday = date(2024, 1, 4, 15, 0, 0);

        assertThat(DateUtils.getWeekBegin(thursday)).isEqualTo(date(2024, 1, 1, 0, 0, 0));
        assertThat(DateUtils.getWeekEnd(thursday)).isEqualTo(date(2024, 1, 7, 0, 0, 0));
        assertThat(DateUtils.getWeekEnd(thursday, true)).isEqualTo(dayEnd(2024, 1, 7));
    }

    @Test
    void monthBoundaries() {
        // February 2024 has 29 days.
        Date day = date(2024, 2, 10, 12, 0, 0);

        assertThat(DateUtils.getMonthBegin(day)).isEqualTo(date(2024, 2, 1, 0, 0, 0));
        assertThat(DateUtils.getMonthEnd(day)).isEqualTo(date(2024, 2, 29, 0, 0, 0));
        assertThat(DateUtils.getMonthEnd(day, true)).isEqualTo(dayEnd(2024, 2, 29));
    }

    @Test
    void quarterBoundaries() {
        Date q1 = date(2024, 2, 10, 12, 0, 0);
        Date q3 = date(2024, 11, 20, 12, 0, 0);

        assertThat(DateUtils.getQuarterBegin(q1)).isEqualTo(date(2024, 1, 1, 0, 0, 0));
        assertThat(DateUtils.getQuarterEnd(q1)).isEqualTo(date(2024, 3, 31, 0, 0, 0));
        assertThat(DateUtils.getQuarterEnd(q1, true)).isEqualTo(dayEnd(2024, 3, 31));
        assertThat(DateUtils.getQuarterBegin(q3)).isEqualTo(date(2024, 10, 1, 0, 0, 0));
        assertThat(DateUtils.getQuarterEnd(q3)).isEqualTo(date(2024, 12, 31, 0, 0, 0));
    }

    @Test
    void listHoursDaysAndMonths() {
        List<Date> hours = DateUtils.listHoursBetweenTime(date(2024, 3, 5, 8, 30, 0), date(2024, 3, 5, 10, 0, 0));
        assertThat(hours).containsExactly(
                date(2024, 3, 5, 8, 0, 0), date(2024, 3, 5, 9, 0, 0), date(2024, 3, 5, 10, 0, 0));

        List<Date> days = DateUtils.listDaysBetweenTime(date(2024, 2, 28, 9, 0, 0), date(2024, 3, 2, 9, 0, 0));
        assertThat(days).containsExactly(
                date(2024, 2, 28, 0, 0, 0), date(2024, 2, 29, 0, 0, 0),
                date(2024, 3, 1, 0, 0, 0), date(2024, 3, 2, 0, 0, 0));

        // Month enumeration preserves the start date's day-of-month, at midnight.
        List<Date> months = DateUtils.listMonthsBetweenTime(date(2023, 11, 15, 9, 30, 0), date(2024, 2, 1, 0, 0, 0));
        assertThat(months).containsExactly(
                date(2023, 11, 15, 0, 0, 0), date(2023, 12, 15, 0, 0, 0),
                date(2024, 1, 15, 0, 0, 0));
    }

    @Test
    void currentTimeEntryPointsAlwaysAnswer() {
        assertThat(DateUtils.getCurrentDateTime()).isCloseTo(new Date(), 120_000L);
        assertThat(DateUtils.getCurrentDateTimeSimpleInterval()).isCloseTo(new Date(), 120_000L);
        assertThat(DateUtils.getSystemDateTime()).isCloseTo(new Date(), 5_000L);
        assertThat(DateUtils.getDateFromDateTimeService()).isNotNull();
    }
}
