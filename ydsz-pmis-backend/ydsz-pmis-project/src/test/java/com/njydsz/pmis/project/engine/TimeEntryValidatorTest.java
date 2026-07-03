package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.TimeEntryDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("工时校验引擎测试")
class TimeEntryValidatorTest {

    @Test
    @DisplayName("正常工时通过校验")
    void shouldPassValidTimeEntry() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setHours(new BigDecimal("8"));
        entry.setEntryDate(LocalDate.now());

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(entry);
        assertTrue(result.ok);
    }

    @Test
    @DisplayName("工时为空返回失败")
    void shouldFailWhenEntryIsNull() {
        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(null);
        assertFalse(result.ok);
        assertEquals("工时为空", result.message);
    }

    @Test
    @DisplayName("工时为零或负数返回失败")
    void shouldFailWhenHoursIsZeroOrNegative() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setHours(BigDecimal.ZERO);

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(entry);
        assertFalse(result.ok);
        assertEquals("工时必须为正数", result.message);
    }

    @Test
    @DisplayName("单日工时超过 24h 返回失败")
    void shouldFailWhenDailyHoursExceed24() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setHours(new BigDecimal("25"));
        entry.setEntryDate(LocalDate.now());

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(entry);
        assertFalse(result.ok);
        assertEquals("单日工时不能超过 24h", result.message);
    }

    @Test
    @DisplayName("加班工时超过 12h 返回失败")
    void shouldFailWhenOvertimeExceeds12() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setHours(new BigDecimal("8"));
        entry.setOvertime(new BigDecimal("13"));
        entry.setEntryDate(LocalDate.now());

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(entry);
        assertFalse(result.ok);
        assertEquals("加班工时不能超过 12h", result.message);
    }

    @Test
    @DisplayName("工时日期为空返回失败")
    void shouldFailWhenEntryDateIsNull() {
        TimeEntryDO entry = new TimeEntryDO();
        entry.setHours(new BigDecimal("8"));

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validate(entry);
        assertFalse(result.ok);
        assertEquals("工时日期不能为空", result.message);
    }

    @Test
    @DisplayName("周工时累计超过 60h 返回失败")
    void shouldFailWhenWeeklyHoursExceed60() {
        TimeEntryDO newEntry = new TimeEntryDO();
        newEntry.setHours(new BigDecimal("10"));

        TimeEntryDO existing = new TimeEntryDO();
        existing.setHours(new BigDecimal("55"));

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validateWeekly(
                newEntry, List.of(existing));
        assertFalse(result.ok);
        assertTrue(result.message.contains("超过上限"));
    }

    @Test
    @DisplayName("周工时累计未超 60h 通过")
    void shouldPassWhenWeeklyHoursNotExceeded() {
        TimeEntryDO newEntry = new TimeEntryDO();
        newEntry.setHours(new BigDecimal("10"));

        TimeEntryDO existing = new TimeEntryDO();
        existing.setHours(new BigDecimal("40"));

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validateWeekly(
                newEntry, List.of(existing));
        assertTrue(result.ok);
    }

    @Test
    @DisplayName("周工时校验 - 跳过自身记录")
    void shouldSkipSelfWhenMatchingId() {
        TimeEntryDO newEntry = new TimeEntryDO();
        newEntry.setId(1L);
        newEntry.setHours(new BigDecimal("10"));

        TimeEntryDO existing = new TimeEntryDO();
        existing.setId(1L);
        existing.setHours(new BigDecimal("55"));

        TimeEntryValidator.ValidationResult result = TimeEntryValidator.validateWeekly(
                newEntry, List.of(existing));
        assertTrue(result.ok);
    }

    @Test
    @DisplayName("折算人天 - 按 8h/天")
    void shouldConvertHoursToDays() {
        BigDecimal days = TimeEntryValidator.toDays(new BigDecimal("16"));
        assertEquals(new BigDecimal("2.00"), days);
    }

    @Test
    @DisplayName("折算人天 - null 返回 0")
    void shouldReturnZeroWhenHoursIsNull() {
        BigDecimal days = TimeEntryValidator.toDays(null);
        assertEquals(BigDecimal.ZERO, days);
    }
}