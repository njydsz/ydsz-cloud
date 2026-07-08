package com.njydsz.pmis.message.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MessageProperties 单元测试：验证默认值与 setter 绑定。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MessagePropertiesTest {

    @Test
    void defaults_areApplied() {
        MessageProperties properties = new MessageProperties();

        assertEquals("NORMAL", properties.getDefaultPriority());
        assertEquals(60000L, properties.getAggregateScanIntervalMs());
        assertEquals(30000L, properties.getRetryScanIntervalMs());
        assertEquals(0, properties.getGlobalDailyLimit());
        assertEquals(0, properties.getGlobalHourlyLimit());
        assertNull(properties.getChannelEnabled());
    }

    @Test
    void channelEnabled_canBeBoundViaSetter() {
        MessageProperties properties = new MessageProperties();
        Map<String, Boolean> enabled = new HashMap<>();
        enabled.put("SMS", true);
        enabled.put("EMAIL", false);
        properties.setChannelEnabled(enabled);

        assertSame(enabled, properties.getChannelEnabled());
        assertEquals(true, properties.getChannelEnabled().get("SMS"));
        assertEquals(false, properties.getChannelEnabled().get("EMAIL"));
    }

    @Test
    void setters_overrideDefaults() {
        MessageProperties properties = new MessageProperties();
        properties.setDefaultPriority("HIGH");
        properties.setAggregateScanIntervalMs(10_000L);
        properties.setRetryScanIntervalMs(5_000L);
        properties.setGlobalDailyLimit(100);
        properties.setGlobalHourlyLimit(20);

        assertEquals("HIGH", properties.getDefaultPriority());
        assertEquals(10_000L, properties.getAggregateScanIntervalMs());
        assertEquals(5_000L, properties.getRetryScanIntervalMs());
        assertEquals(100, properties.getGlobalDailyLimit());
        assertEquals(20, properties.getGlobalHourlyLimit());
    }

    // ==================== P2-5: SmartTimingConfig ====================

    @Test
    @DisplayName("SmartTimingConfig 默认值：enabled=true / urgentBypass=true / 5 打扰通道 / buffer=60s / maxDefer=72h")
    void smartTiming_defaults_areApplied() {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();

        assertTrue(stc.isEnabled());
        assertTrue(stc.isUrgentBypassDnd());
        assertEquals(60L, stc.getDndBufferSeconds());
        assertEquals(72L, stc.getMaxDeferHours());
        assertEquals(5, stc.getDisruptiveChannels().size());
        assertEquals(Arrays.asList("SMS", "PUSH", "DINGTALK", "WECOM", "FEISHU"),
                stc.getDisruptiveChannels());
    }

    @Test
    @DisplayName("isDisruptive: SMS/PUSH/DINGTALK/WECOM/FEISHU → true；EMAIL/INAPP/WEBHOOK/null → false")
    void smartTiming_isDisruptive_shouldMatchDefaultChannels() {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();

        assertTrue(stc.isDisruptive("SMS"));
        assertTrue(stc.isDisruptive("PUSH"));
        assertTrue(stc.isDisruptive("DINGTALK"));
        assertTrue(stc.isDisruptive("WECOM"));
        assertTrue(stc.isDisruptive("FEISHU"));
        // 小写也能匹配
        assertTrue(stc.isDisruptive("sms"));
        // 非打扰型通道
        assertFalse(stc.isDisruptive("EMAIL"));
        assertFalse(stc.isDisruptive("INAPP"));
        assertFalse(stc.isDisruptive("WEBHOOK"));
        // null 安全
        assertFalse(stc.isDisruptive(null));
    }

    @Test
    @DisplayName("isDisruptive: 自定义通道列表后按新列表判断")
    void smartTiming_isDisruptive_shouldRespectCustomChannels() {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();
        stc.setDisruptiveChannels(Arrays.asList("SMS", "VOICE"));

        assertTrue(stc.isDisruptive("SMS"));
        assertTrue(stc.isDisruptive("VOICE"));
        // PUSH 不再是打扰型
        assertFalse(stc.isDisruptive("PUSH"));
    }

    @Test
    @DisplayName("disruptiveChannelSet: 返回独立副本,修改不影响原配置")
    void smartTiming_disruptiveChannelSet_shouldReturnCopy() {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();

        assertEquals(5, stc.disruptiveChannelSet().size());
        // 修改返回的 set 不影响原配置
        stc.disruptiveChannelSet().add("NEW_CHANNEL");
        assertEquals(5, stc.getDisruptiveChannels().size());
    }

    @Test
    @DisplayName("SmartTimingConfig setter 覆盖默认值")
    void smartTiming_setters_overrideDefaults() {
        MessageProperties.SmartTimingConfig stc = new MessageProperties.SmartTimingConfig();
        stc.setEnabled(false);
        stc.setUrgentBypassDnd(false);
        stc.setDndBufferSeconds(120L);
        stc.setMaxDeferHours(24L);
        stc.setDisruptiveChannels(Arrays.asList("SMS"));

        assertFalse(stc.isEnabled());
        assertFalse(stc.isUrgentBypassDnd());
        assertEquals(120L, stc.getDndBufferSeconds());
        assertEquals(24L, stc.getMaxDeferHours());
        assertEquals(1, stc.getDisruptiveChannels().size());
    }

    @Test
    @DisplayName("MessageProperties.smartTiming 默认非 null")
    void smartTiming_shouldBeNonNullByDefault() {
        MessageProperties properties = new MessageProperties();

        assertNotNull(properties.getSmartTiming());
        assertTrue(properties.getSmartTiming().isEnabled());
    }
}
