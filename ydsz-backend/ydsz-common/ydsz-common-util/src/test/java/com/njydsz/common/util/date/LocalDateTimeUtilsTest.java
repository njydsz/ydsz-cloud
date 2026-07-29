package com.njydsz.common.util.date;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * LocalDateTimeUtils 单元测试
 *
 * <p>覆盖核心方法：格式化、解析、计算、比较、判断等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("LocalDateTimeUtils 工具类测试")
class LocalDateTimeUtilsTest {

    private static final String DATE_STR = "2024-01-15";
    private static final String DATETIME_STR = "2024-01-15 10:30:00";
    private static final LocalDateTime DT = LocalDateTime.of(2024, 1, 15, 10, 30, 0);

    // ==================== 格式化/解析 ====================

    @Nested
    @DisplayName("格式化/解析")
    class FormatParseTest {

    @Test
    @DisplayName("格式化日期")
    void formatDate() {
        assertThat(LocalDateTimeUtils.format(DT)).isEqualTo(DATETIME_STR);
        assertThat(LocalDateTimeUtils.format(null)).isNull();
    }

    @Test
    @DisplayName("格式化日期 - 自定义 pattern")
    void formatDate_pattern() {
        assertThat(LocalDateTimeUtils.format(DT, "yyyy-MM-dd")).isEqualTo(DATE_STR);
    }

    @Test
    @DisplayName("解析日期时间")
    void parseDateTime() {
        LocalDateTime result = LocalDateTimeUtils.parse(DATETIME_STR);
        assertThat(result).isEqualTo(DT);
    }
    }

    // ==================== 判断方法 ====================

    @Nested
    @DisplayName("判断方法")
    class CheckTest {

        @Test
        @DisplayName("判断周末")
        void isWeekend() {
            // 2024-01-15 是星期一
            assertThat(LocalDateTimeUtils.isWeekend(DT)).isFalse();
            // 2024-01-20 是星期六
            LocalDateTime saturday = LocalDateTime.of(2024, 1, 20, 10, 0);
            assertThat(LocalDateTimeUtils.isWeekend(saturday)).isTrue();
            // 2024-01-21 是星期日
            LocalDateTime sunday = LocalDateTime.of(2024, 1, 21, 10, 0);
            assertThat(LocalDateTimeUtils.isWeekend(sunday)).isTrue();
            assertThat(LocalDateTimeUtils.isWeekend(null)).isFalse();
        }

        @Test
        @DisplayName("判断闰年")
        void isLeapYear() {
            assertThat(LocalDateTimeUtils.isLeapYear(LocalDateTime.of(2024, 1, 1, 0, 0))).isTrue();
            assertThat(LocalDateTimeUtils.isLeapYear(LocalDateTime.of(2023, 1, 1, 0, 0))).isFalse();
            assertThat(LocalDateTimeUtils.isLeapYear(LocalDateTime.of(2000, 1, 1, 0, 0))).isTrue();
            assertThat(LocalDateTimeUtils.isLeapYear(LocalDateTime.of(1900, 1, 1, 0, 0))).isFalse();
        }

        @Test
        @DisplayName("判断今天")
        void isToday() {
            LocalDateTime now = LocalDateTime.now();
            assertThat(LocalDateTimeUtils.isToday(now)).isTrue();
            assertThat(LocalDateTimeUtils.isToday(DT)).isFalse();
            assertThat(LocalDateTimeUtils.isToday(null)).isFalse();
        }

        @Test
        @DisplayName("获取星期几")
        void getDayOfWeek() {
            assertThat(LocalDateTimeUtils.getDayOfWeek(DT)).isEqualTo(DayOfWeek.MONDAY);
        }
    }

    // ==================== 计算方法 ====================

    @Nested
    @DisplayName("计算方法")
    class CalculateTest {

        @Test
        @DisplayName("加减天数")
        void plusDays() {
            LocalDateTime result = LocalDateTimeUtils.plusDays(DT, 5);
            assertThat(result).isEqualTo(DT.plusDays(5));
        }

        @Test
        @DisplayName("计算天数差")
        void daysBetween() {
            LocalDateTime end = DT.plusDays(10);
            long days = LocalDateTimeUtils.betweenDays(DT, end);
            assertThat(days).isEqualTo(10);
        }

        @Test
        @DisplayName("计算小时差")
        void hoursBetween() {
            LocalDateTime end = DT.plusHours(5);
            long hours = LocalDateTimeUtils.betweenHours(DT, end);
            assertThat(hours).isEqualTo(5);
        }

        @Test
        @DisplayName("下一个工作日")
        void nextWeekday() {
            // 2024-01-15 是星期一，下一个工作日是 2024-01-16 星期二
            LocalDateTime next = LocalDateTimeUtils.nextWeekday(DT);
            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        }

        @Test
        @DisplayName("下一个工作日 - 从周五")
        void nextWeekday_fromFriday() {
            // 2024-01-19 是星期五，下一个工作日是 2024-01-22 星期一
            LocalDateTime friday = LocalDateTime.of(2024, 1, 19, 10, 0);
            LocalDateTime next = LocalDateTimeUtils.nextWeekday(friday);
            assertThat(next.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
            assertThat(LocalDateTimeUtils.nextWeekday(null)).isNull();
        }
    }

    // ==================== 范围方法 ====================

    @Test
    @DisplayName("判断是否在范围内")
    void isBetween() {
        LocalDateTime start = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2024, 12, 31, 23, 59);
        assertThat(LocalDateTimeUtils.isBetween(DT, start, end)).isTrue();

        LocalDateTime before = LocalDateTime.of(2023, 6, 1, 0, 0);
        assertThat(LocalDateTimeUtils.isBetween(before, start, end)).isFalse();
    }

    // ==================== 季度方法 ====================

    @Test
    @DisplayName("获取季度")
    void getQuarter() {
        assertThat(LocalDateTimeUtils.getQuarter(DT)).isEqualTo(1);
        assertThat(LocalDateTimeUtils.getQuarter(LocalDateTime.of(2024, 5, 1, 0, 0))).isEqualTo(2);
        assertThat(LocalDateTimeUtils.getQuarter(LocalDateTime.of(2024, 8, 1, 0, 0))).isEqualTo(3);
        assertThat(LocalDateTimeUtils.getQuarter(LocalDateTime.of(2024, 11, 1, 0, 0))).isEqualTo(4);
    }
}
