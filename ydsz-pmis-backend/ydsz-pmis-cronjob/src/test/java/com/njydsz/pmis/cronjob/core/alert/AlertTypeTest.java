package com.njydsz.pmis.cronjob.core.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AlertType} 枚举测试（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertType 告警类型枚举测试")
class AlertTypeTest {

    @Test
    @DisplayName("parse: null 返回 null")
    void parse_null_returnsNull() {
        assertNull(AlertType.parse(null));
    }

    @Test
    @DisplayName("parse: 空白字符串返回 null")
    void parse_blank_returnsNull() {
        assertNull(AlertType.parse(""));
        assertNull(AlertType.parse("   "));
    }

    @Test
    @DisplayName("parse: 大小写不敏感")
    void parse_caseInsensitive_returnsEnum() {
        assertEquals(AlertType.FAIL, AlertType.parse("fail"));
        assertEquals(AlertType.FAIL, AlertType.parse("FAIL"));
        assertEquals(AlertType.FAIL, AlertType.parse(" Fail "));
        assertEquals(AlertType.FAIL_RATE, AlertType.parse("fail_rate"));
        assertEquals(AlertType.DURATION_P95, AlertType.parse("duration_p95"));
    }

    @Test
    @DisplayName("parse: 无效值返回 null")
    void parse_invalid_returnsNull() {
        assertNull(AlertType.parse("UNKNOWN"));
        assertNull(AlertType.parse("foobar"));
    }

    @Test
    @DisplayName("requiresThreshold: FAIL/TIMEOUT 不需要阈值")
    void requiresThreshold_failAndTimeout_false() {
        assertFalse(AlertType.FAIL.requiresThreshold());
        assertFalse(AlertType.TIMEOUT.requiresThreshold());
    }

    @Test
    @DisplayName("requiresThreshold: SLOW/FAIL_RATE/DURATION_P95 需要阈值")
    void requiresThreshold_others_true() {
        assertTrue(AlertType.SLOW.requiresThreshold());
        assertTrue(AlertType.FAIL_RATE.requiresThreshold());
        assertTrue(AlertType.DURATION_P95.requiresThreshold());
    }

    @Test
    @DisplayName("requiresTimeWindow: FAIL_RATE/DURATION_P95 需要时间窗口")
    void requiresTimeWindow_rateAndDuration_true() {
        assertTrue(AlertType.FAIL_RATE.requiresTimeWindow());
        assertTrue(AlertType.DURATION_P95.requiresTimeWindow());
    }

    @Test
    @DisplayName("requiresTimeWindow: FAIL/TIMEOUT/SLOW 不需要时间窗口")
    void requiresTimeWindow_others_false() {
        assertFalse(AlertType.FAIL.requiresTimeWindow());
        assertFalse(AlertType.TIMEOUT.requiresTimeWindow());
        assertFalse(AlertType.SLOW.requiresTimeWindow());
    }
}
