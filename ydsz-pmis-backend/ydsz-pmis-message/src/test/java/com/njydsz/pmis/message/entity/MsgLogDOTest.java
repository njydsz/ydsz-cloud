package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgLogDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgLogDOTest {

    @Test
    void testGetterSetter() {
        MsgLogDO entity = new MsgLogDO();
        entity.setId("log-1");
        entity.setChannel("EMAIL");
        entity.setStatus("SUCCESS");
        entity.setCanary(0);
        entity.setCostMs(120L);
        entity.setRetryCount(2);
        entity.setReconsumeTimes(1);
        entity.setReceiptAt(LocalDateTime.now());

        assertEquals("log-1", entity.getId());
        assertEquals("EMAIL", entity.getChannel());
        assertEquals("SUCCESS", entity.getStatus());
        assertEquals(0, entity.getCanary());
        assertEquals(120L, entity.getCostMs());
        assertEquals(2, entity.getRetryCount());
        assertEquals(1, entity.getReconsumeTimes());
        assertNotNull(entity.getReceiptAt());
        assertNotNull(entity);
    }
}
