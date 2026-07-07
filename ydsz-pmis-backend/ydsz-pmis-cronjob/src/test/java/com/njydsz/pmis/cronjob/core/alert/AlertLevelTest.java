package com.njydsz.pmis.cronjob.core.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AlertLevel} 枚举测试（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertLevel 告警级别枚举测试")
class AlertLevelTest {

    @Test
    @DisplayName("parse: null 返回 WARN 默认值")
    void parse_null_returnsWarn() {
        assertEquals(AlertLevel.WARN, AlertLevel.parse(null));
    }

    @Test
    @DisplayName("parse: 空白字符串返回 WARN 默认值")
    void parse_blank_returnsWarn() {
        assertEquals(AlertLevel.WARN, AlertLevel.parse(""));
        assertEquals(AlertLevel.WARN, AlertLevel.parse("   "));
    }

    @Test
    @DisplayName("parse: 大小写不敏感")
    void parse_caseInsensitive_returnsEnum() {
        assertEquals(AlertLevel.INFO, AlertLevel.parse("info"));
        assertEquals(AlertLevel.WARN, AlertLevel.parse("warn"));
        assertEquals(AlertLevel.ERROR, AlertLevel.parse("error"));
        assertEquals(AlertLevel.CRITICAL, AlertLevel.parse("critical"));
        assertEquals(AlertLevel.CRITICAL, AlertLevel.parse(" CRITICAL "));
    }

    @Test
    @DisplayName("parse: 无效值返回 WARN 默认值")
    void parse_invalid_returnsWarn() {
        assertEquals(AlertLevel.WARN, AlertLevel.parse("UNKNOWN"));
        assertEquals(AlertLevel.WARN, AlertLevel.parse("fatal"));
    }
}
