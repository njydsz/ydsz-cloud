package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgPreferenceDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgPreferenceDOTest {

    @Test
    void testGetterSetter() {
        MsgPreferenceDO entity = new MsgPreferenceDO();
        entity.setId("p-1");
        entity.setUserId("u-1");
        entity.setChannel("SMS");
        entity.setEnabled(1);
        entity.setDndEnabled(0);
        entity.setDailyLimit(100);
        entity.setHourlyLimit(20);
        entity.setDigestEnabled(0);

        assertEquals("p-1", entity.getId());
        assertEquals("u-1", entity.getUserId());
        assertEquals("SMS", entity.getChannel());
        assertEquals(1, entity.getEnabled());
        assertEquals(0, entity.getDndEnabled());
        assertEquals(100, entity.getDailyLimit());
        assertEquals(20, entity.getHourlyLimit());
        assertEquals(0, entity.getDigestEnabled());
        assertNotNull(entity);
    }
}
