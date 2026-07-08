package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgSubscriptionDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgSubscriptionDOTest {

    @Test
    void testGetterSetter() {
        MsgSubscriptionDO entity = new MsgSubscriptionDO();
        entity.setId("s-1");
        entity.setUserId("u-1");
        entity.setTopicCode("RISK_ALERT");
        entity.setChannel("INAPP");
        entity.setStatus("SUBSCRIBED");

        assertEquals("s-1", entity.getId());
        assertEquals("u-1", entity.getUserId());
        assertEquals("RISK_ALERT", entity.getTopicCode());
        assertEquals("INAPP", entity.getChannel());
        assertEquals("SUBSCRIBED", entity.getStatus());
        assertNotNull(entity);
    }
}
