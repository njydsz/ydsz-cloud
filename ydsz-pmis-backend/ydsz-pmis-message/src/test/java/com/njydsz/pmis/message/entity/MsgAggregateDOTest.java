package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgAggregateDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgAggregateDOTest {

    @Test
    void testGetterSetter() {
        MsgAggregateDO entity = new MsgAggregateDO();
        entity.setId("a-1");
        entity.setAggregateGroup("RISK:contract-1");
        entity.setReceiver("u-1");
        entity.setChannel("IN_APP");
        entity.setBatchStatus("PENDING");
        entity.setMessageCount(3);
        entity.setFirstMessageAt(LocalDateTime.now());
        entity.setScheduledSendAt(LocalDateTime.now().plusHours(1));

        assertEquals("a-1", entity.getId());
        assertEquals("RISK:contract-1", entity.getAggregateGroup());
        assertEquals("u-1", entity.getReceiver());
        assertEquals("IN_APP", entity.getChannel());
        assertEquals("PENDING", entity.getBatchStatus());
        assertEquals(3, entity.getMessageCount());
        assertNotNull(entity.getFirstMessageAt());
        assertNotNull(entity.getScheduledSendAt());
        assertNotNull(entity);
    }
}
