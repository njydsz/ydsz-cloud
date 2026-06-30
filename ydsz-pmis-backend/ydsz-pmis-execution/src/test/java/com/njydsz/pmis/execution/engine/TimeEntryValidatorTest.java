package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.entity.TimeEntryDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimeEntryValidator 工时校验器测试")
class TimeEntryValidatorTest {

    @Test
    @DisplayName("null/空工时")
    void validateEmpty() {
        assertThat(TimeEntryValidator.validate(null).ok).isFalse();
        TimeEntryDO e = new TimeEntryDO();
        assertThat(TimeEntryValidator.validate(e).ok).isFalse();
    }

    @Test
    @DisplayName("工时必须为正数")
    void validateZero() {
        TimeEntryDO e = new TimeEntryDO();
        e.setHours(BigDecimal.ZERO);
        e.setEntryDate(LocalDate.now());
        assertThat(TimeEntryValidator.validate(e).ok).isFalse();
    }

    @Test
    @DisplayName("单日 24h 上限")
    void validateDaily() {
        TimeEntryDO e = new TimeEntryDO();
        e.setHours(new BigDecimal("25"));
        e.setEntryDate(LocalDate.now());
        assertThat(TimeEntryValidator.validate(e).ok).isFalse();
    }

    @Test
    @DisplayName("加班 12h 上限")
    void validateOvertime() {
        TimeEntryDO e = new TimeEntryDO();
        e.setHours(new BigDecimal("10"));
        e.setOvertime(new BigDecimal("13"));
        e.setEntryDate(LocalDate.now());
        assertThat(TimeEntryValidator.validate(e).ok).isFalse();
    }

    @Test
    @DisplayName("合法工时")
    void validateOk() {
        TimeEntryDO e = new TimeEntryDO();
        e.setHours(new BigDecimal("8"));
        e.setOvertime(new BigDecimal("2"));
        e.setEntryDate(LocalDate.now());
        assertThat(TimeEntryValidator.validate(e).ok).isTrue();
    }

    @Test
    @DisplayName("周工时累计上限 60h")
    void validateWeekly() {
        TimeEntryDO e = new TimeEntryDO();
        e.setHours(new BigDecimal("10"));
        e.setEntryDate(LocalDate.now());
        java.util.List<TimeEntryDO> week = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TimeEntryDO w = new TimeEntryDO();
            w.setHours(new BigDecimal("10"));
            week.add(w);
        }
        // 现有 50h + 新的 10h = 60h
        assertThat(TimeEntryValidator.validateWeekly(e, week).ok).isTrue();
        week.add(new TimeEntryDO() {{ setHours(new BigDecimal("5")); }});
        // 50h + 5h + 10h = 65h 超过
        assertThat(TimeEntryValidator.validateWeekly(e, week).ok).isFalse();
    }

    @Test
    @DisplayName("人天折算")
    void toDays() {
        assertThat(TimeEntryValidator.toDays(new BigDecimal("8"))).isEqualByComparingTo("1.00");
        assertThat(TimeEntryValidator.toDays(new BigDecimal("4"))).isEqualByComparingTo("0.50");
        assertThat(TimeEntryValidator.toDays(null)).isEqualByComparingTo("0.00");
    }
}
