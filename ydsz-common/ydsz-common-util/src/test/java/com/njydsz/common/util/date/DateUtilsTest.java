package com.njydsz.common.util.date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link DateUtils} 单元测试。
 *
 * <p>覆盖：边界计算、差值、工作日、闰年、年龄、时区、季度、区间重叠。
 *
 * @since 26.09.19
 */
@DisplayName("DateUtils 测试")
class DateUtilsTest {

  @Nested
  @DisplayName("边界计算")
  class BoundaryTest {

    @Test
    @DisplayName("getStartOfDay: 返回 00:00:00")
    void getStartOfDay_returnsMidnight() {
      LocalDateTime result = DateUtils.getStartOfDay(LocalDate.of(2026, 9, 19));
      assertThat(result.toLocalTime().toString()).isEqualTo("00:00");
    }

    @Test
    @DisplayName("getEndOfDay: 返回 23:59:59.999999999")
    void getEndOfDay_returnsEndOfDay() {
      LocalDateTime result = DateUtils.getEndOfDay(LocalDate.of(2026, 9, 19));
      assertThat(result.getHour()).isEqualTo(23);
      assertThat(result.getMinute()).isEqualTo(59);
      assertThat(result.getSecond()).isEqualTo(59);
    }

    @Test
    @DisplayName("getStartOfMonth: 返回当月 1 号")
    void getStartOfMonth_returnsFirstDayOfMonth() {
      LocalDateTime result = DateUtils.getStartOfMonth(LocalDate.of(2026, 9, 19));
      assertThat(result.getDayOfMonth()).isEqualTo(1);
      assertThat(result.getMonth()).isEqualTo(SEPTEMBER);
    }

    @Test
    @DisplayName("getEndOfMonth: 返回当月最后一天")
    void getEndOfMonth_returnsLastDayOfMonth() {
      LocalDateTime result = DateUtils.getEndOfMonth(LocalDate.of(2026, 2, 15));
      // 2026 非闰年，2 月最后一天 = 28
      assertThat(result.getDayOfMonth()).isEqualTo(28);
    }

    @Test
    @DisplayName("getStartOfQuarter: Q3 返回 7 月 1 日")
    void getStartOfQuarter_q3_returnsJulyFirst() {
      LocalDateTime result = DateUtils.getStartOfQuarter(LocalDate.of(2026, 9, 19));
      assertThat(result.getMonth()).isEqualTo(JULY);
      assertThat(result.getDayOfMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("getEndOfQuarter: Q4 返回 12 月 31 日")
    void getEndOfQuarter_q4_returnsDecThirtyFirst() {
      LocalDateTime result = DateUtils.getEndOfQuarter(LocalDate.of(2026, 11, 15));
      assertThat(result.getMonth()).isEqualTo(DECEMBER);
      assertThat(result.getDayOfMonth()).isEqualTo(31);
    }
  }

  @Nested
  @DisplayName("季度边界(闰年)")
  class LeapYearQuarterTest {

    @Test
    @DisplayName("getStartOfQuarter: 闰年 Q1 返回 1 月 1 日")
    void getStartOfQuarter_leapYear_q1() {
      LocalDateTime result = DateUtils.getStartOfQuarter(LocalDate.of(2024, 2, 29));
      assertThat(result.getMonth()).isEqualTo(JANUARY);
      assertThat(result.getDayOfMonth()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("日期差值")
  class DiffTest {

    @Test
    @DisplayName("daysBetween: 正序返回正数")
    void daysBetween_forward_returnsPositive() {
      long days = DateUtils.daysBetween(
          LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 10));
      assertThat(days).isEqualTo(9);
    }

    @Test
    @DisplayName("daysBetween: 逆序返回负数")
    void daysBetween_backward_returnsNegative() {
      long days = DateUtils.daysBetween(
          LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1));
      assertThat(days).isEqualTo(-9);
    }

    @Test
    @DisplayName("monthsBetween: 正确计算月数差")
    void monthsBetween_correct() {
      long months = DateUtils.monthsBetween(
          LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
      assertThat(months).isEqualTo(11);
    }
  }

  @Nested
  @DisplayName("判断方法")
  class JudgeTest {

    @Test
    @DisplayName("isLeapYear: 闰年返回 true")
    void isLeapYear_2024_returnsTrue() {
      assertThat(DateUtils.isLeapYear(2024)).isTrue();
    }

    @Test
    @DisplayName("isLeapYear: 非闰年返回 false")
    void isLeapYear_2026_returnsFalse() {
      assertThat(DateUtils.isLeapYear(2026)).isFalse();
    }

    @Test
    @DisplayName("isWeekend: 周六/周日返回 true")
    void isWeekend_saturdayAndSunday() {
      assertThat(DateUtils.isWeekend(LocalDate.of(2026, 9, 19))).isTrue(); // Saturday
      assertThat(DateUtils.isWeekend(LocalDate.of(2026, 9, 20))).isTrue(); // Sunday
    }

    @Test
    @DisplayName("isWeekend: 周一返回 false")
    void isWeekend_monday_returnsFalse() {
      assertThat(DateUtils.isWeekend(LocalDate.of(2026, 9, 21))).isFalse();
    }

    @Test
    @DisplayName("isSameDay: 同一天返回 true")
    void isSameDay_sameDay_returnsTrue() {
      assertThat(DateUtils.isSameDay(LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 19))).isTrue();
    }

    @Test
    @DisplayName("isSameDay: 任一方 null 返回 false")
    void isSameDay_oneNull_returnsFalse() {
      assertThat(DateUtils.isSameDay(null, LocalDate.of(2026, 9, 19))).isFalse();
    }
  }

  @Nested
  @DisplayName("工作日计算")
  class BusinessDayTest {

    @Test
    @DisplayName("addBusinessDays: 增加工作日跳过周末")
    void addBusinessDays_skipsWeekend() {
      // 2026-09-18 是周五，+1 个工作日 = 下周一 2026-09-21
      LocalDate result = DateUtils.addBusinessDays(LocalDate.of(2026, 9, 18), 1);
      assertThat(result).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    @DisplayName("addBusinessDays: 负数向过去推算")
    void addBusinessDays_negative() {
      // 2026-09-21 是周一，-1 个工作日 = 上周五 2026-09-18
      LocalDate result = DateUtils.addBusinessDays(LocalDate.of(2026, 9, 21), -1);
      assertThat(result).isEqualTo(LocalDate.of(2026, 9, 18));
    }
  }

  @Nested
  @DisplayName("时区转换")
  class TimezoneTest {

    @Test
    @DisplayName("atZone: Instant → ZonedDateTime 正确转换")
    void atZone_instant_correctConversion() {
      Instant instant = Instant.parse("2026-09-19T12:00:00Z");
      ZonedDateTime result = DateUtils.atZone(instant, ZoneId.of("Asia/Shanghai"));
      // UTC+8，所以 12:00 UTC = 20:00 CST
      assertThat(result.getHour()).isEqualTo(20);
    }

    @Test
    @DisplayName("atZone: LocalDateTime → ZonedDateTime 正确转换")
    void atZone_localDateTime_correctConversion() {
      LocalDateTime localDateTime = LocalDateTime.of(2026, 9, 19, 12, 0);
      ZonedDateTime result = DateUtils.atZone(localDateTime, ZoneId.of("UTC"));
      assertThat(result.getHour()).isEqualTo(12);
      assertThat(result.getZone()).isEqualTo(ZoneId.of("UTC"));
    }
  }

  @Nested
  @DisplayName("区间重叠")
  class OverlapTest {

    @Test
    @DisplayName("isOverlap: 重叠区间返回 true")
    void isOverlap_overlapping_returnsTrue() {
      boolean result = DateUtils.isOverlap(
          LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
          LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15));
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isOverlap: 端点相交返回 true（闭区间）")
    void isOverlap_endpointAdjacent_returnsTrue() {
      boolean result = DateUtils.isOverlap(
          LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
          LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 15));
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isOverlap: 分离区间返回 false")
    void isOverlap_disjoint_returnsFalse() {
      boolean result = DateUtils.isOverlap(
          LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
          LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isOverlap: 输入区间反向抛出异常")
    void isOverlap_endBeforeStart_throws() {
      assertThatThrownBy(() -> DateUtils.isOverlap(
          LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1),
          LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // 引用静态导入的月份
  private static final Month SEPTEMBER = Month.SEPTEMBER;
  private static final Month JULY = Month.JULY;
  private static final Month DECEMBER = Month.DECEMBER;
  private static final Month JANUARY = Month.JANUARY;
}
