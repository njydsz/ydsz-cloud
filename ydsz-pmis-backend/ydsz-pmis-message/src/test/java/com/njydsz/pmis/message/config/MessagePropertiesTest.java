package com.njydsz.pmis.message.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
