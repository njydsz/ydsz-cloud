package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.TimeEntryDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TimeEntryValidator 单元测试
 */
@DisplayName("TimeEntryValidator 工时校验测试")
class TimeEntryValidatorTest {

    @Test
    @DisplayName("空工时应校验失败")
    void validate_null() {
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(null);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("为空");
    }

    @Test
    @DisplayName("负数工时应校验失败")
    void validate_negative() {
        TimeEntryDO e = entry(1L, new BigDecimal("-1"));
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(e);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("正数");
    }

    @Test
    @DisplayName("0 工时应校验失败")
    void validate_zero() {
        TimeEntryDO e = entry(1L, BigDecimal.ZERO);
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(e);
        assertThat(r.ok).isFalse();
    }

    @Test
    @DisplayName("单日 24h 边界应放行（使用 > 比较）")
    void validate_dailyBoundary() {
        TimeEntryDO e = entry(1L, new BigDecimal("24"));
        assertThat(TimeEntryValidator.validate(e).ok).isTrue();
    }

    @Test
    @DisplayName("单日 25h 应校验失败")
    void validate_dailyOverflow() {
        TimeEntryDO e = entry(1L, new BigDecimal("25"));
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(e);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("24");
    }

    @Test
    @DisplayName("加班 12h 应放行")
    void validate_overtimeBoundary() {
        TimeEntryDO e = entry(1L, new BigDecimal("8"));
        e.setOvertime(new BigDecimal("12"));
        assertThat(TimeEntryValidator.validate(e).ok).isTrue();
    }

    @Test
    @DisplayName("加班 13h 应校验失败")
    void validate_overtimeOverflow() {
        TimeEntryDO e = entry(1L, new BigDecimal("8"));
        e.setOvertime(new BigDecimal("13"));
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(e);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("12");
    }

    @Test
    @DisplayName("加班为空应放行")
    void validate_overtimeNull() {
        TimeEntryDO e = entry(1L, new BigDecimal("8"));
        e.setOvertime(null);
        assertThat(TimeEntryValidator.validate(e).ok).isTrue();
    }

    @Test
    @DisplayName("日期为空应校验失败")
    void validate_dateNull() {
        TimeEntryDO e = entry(1L, new BigDecimal("8"));
        e.setEntryDate(null);
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validate(e);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("日期");
    }

    @Test
    @DisplayName("正常工时应放行")
    void validate_ok() {
        TimeEntryDO e = entry(1L, new BigDecimal("8"));
        assertThat(TimeEntryValidator.validate(e).ok).isTrue();
    }

    @Test
    @DisplayName("周累计 60h 边界应放行（> 比较）")
    void validateWeekly_boundary() {
        TimeEntryDO newE = entry(1L, new BigDecimal("8"));
        TimeEntryDO e1 = entry(2L, new BigDecimal("8"));
        TimeEntryDO e2 = entry(3L, new BigDecimal("8"));
        TimeEntryDO e3 = entry(4L, new BigDecimal("8"));
        TimeEntryDO e4 = entry(5L, new BigDecimal("8"));
        TimeEntryDO e5 = entry(6L, new BigDecimal("8"));
        TimeEntryDO e6 = entry(7L, new BigDecimal("8"));
        TimeEntryDO e7 = entry(8L, new BigDecimal("4"));
        // 7*8 + 4 = 60
        List<TimeEntryDO> week = List.of(e1, e2, e3, e4, e5, e6, e7);
        assertThat(TimeEntryValidator.validateWeekly(newE, week).ok).isTrue();
    }

    @Test
    @DisplayName("周累计 60.5h 应校验失败")
    void validateWeekly_overload() {
        TimeEntryDO newE = entry(1L, new BigDecimal("0.5"));
        TimeEntryDO e1 = entry(2L, new BigDecimal("60"));
        List<TimeEntryDO> week = List.of(e1);
        TimeEntryValidator.ValidationResult r = TimeEntryValidator.validateWeekly(newE, week);
        assertThat(r.ok).isFalse();
        assertThat(r.message).contains("60");
    }

    @Test
    @DisplayName("周累计同 ID 应跳过自身")
    void validateWeekly_skipSelf() {
        TimeEntryDO e1 = entry(1L, new BigDecimal("30"));
        TimeEntryDO e2 = entry(2L, new BigDecimal("30"));
        // 新工时 = 10, 同 ID 应跳过, 实际 10+30 = 40
        TimeEntryDO newE = entry(1L, new BigDecimal("10"));
        List<TimeEntryDO> week = List.of(e1, e2);
        assertThat(TimeEntryValidator.validateWeekly(newE, week).ok).isTrue();
    }

    @Test
    @DisplayName("新工时为 null 应放行")
    void validateWeekly_nullEntry() {
        assertThat(TimeEntryValidator.validateWeekly(null, List.of()).ok).isTrue();
    }

    @Test
    @DisplayName("新工时 hours 为 null 应放行")
    void validateWeekly_nullHours() {
        TimeEntryDO e = entry(1L, null);
        assertThat(TimeEntryValidator.validateWeekly(e, List.of()).ok).isTrue();
    }

    @Test
    @DisplayName("toDays 8h = 1 人天")
    void toDays_eight() {
        assertThat(TimeEntryValidator.toDays(new BigDecimal("8"))).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("toDays 4h = 0.5 人天")
    void toDays_half() {
        assertThat(TimeEntryValidator.toDays(new BigDecimal("4"))).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("toDays 空值应返回 0")
    void toDays_null() {
        assertThat(TimeEntryValidator.toDays(null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("常量值符合预期")
    void constants() {
        assertThat(TimeEntryValidator.MAX_DAILY_HOURS).isEqualByComparingTo("24");
        assertThat(TimeEntryValidator.MAX_WEEKLY_HOURS).isEqualByComparingTo("60");
        assertThat(TimeEntryValidator.MAX_OVERTIME_HOURS).isEqualByComparingTo("12");
        assertThat(TimeEntryValidator.CONSECUTIVE_MISSING_DAYS).isEqualTo(3);
    }

    private TimeEntryDO entry(Long id, BigDecimal hours) {
        TimeEntryDO e = new TimeEntryDO();
        e.setId(id);
        e.setHours(hours);
        e.setEntryDate(LocalDate.of(2026, 1, 1));
        e.setEmployeeId(100L);
        return e;
    }
}
