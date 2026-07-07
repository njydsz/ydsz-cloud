package com.njydsz.pmis.cronjob.core.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AlertChannel} 枚举测试（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AlertChannel 告警通道枚举测试")
class AlertChannelTest {

    @Test
    @DisplayName("parse: null 返回 null")
    void parse_null_returnsNull() {
        assertNull(AlertChannel.parse(null));
    }

    @Test
    @DisplayName("parse: 空白字符串返回 null")
    void parse_blank_returnsNull() {
        assertNull(AlertChannel.parse(""));
        assertNull(AlertChannel.parse("   "));
    }

    @Test
    @DisplayName("parse: 大小写不敏感")
    void parse_caseInsensitive_returnsEnum() {
        assertEquals(AlertChannel.EMAIL, AlertChannel.parse("email"));
        assertEquals(AlertChannel.EMAIL, AlertChannel.parse("EMAIL"));
        assertEquals(AlertChannel.EMAIL, AlertChannel.parse(" Email "));
        assertEquals(AlertChannel.DINGTALK, AlertChannel.parse("dingtalk"));
        assertEquals(AlertChannel.WECOM, AlertChannel.parse("wecom"));
        assertEquals(AlertChannel.WEBHOOK, AlertChannel.parse("webhook"));
        assertEquals(AlertChannel.FEISHU, AlertChannel.parse("feishu"));
        assertEquals(AlertChannel.SMS, AlertChannel.parse("sms"));
    }

    @Test
    @DisplayName("parse: 无效值返回 null")
    void parse_invalid_returnsNull() {
        assertNull(AlertChannel.parse("SLACK"));
        assertNull(AlertChannel.parse("unknown"));
    }
}
