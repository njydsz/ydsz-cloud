package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgRouteRuleDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgRouteRuleDOTest {

    @Test
    void testGetterSetter() {
        MsgRouteRuleDO entity = new MsgRouteRuleDO();
        entity.setId("r-1");
        entity.setRuleCode("RULE_001");
        entity.setRuleName("预警路由");
        entity.setPriority(100);
        entity.setTargetChannel("SMS");
        entity.setFallbackChannel("EMAIL");
        entity.setSortOrder(1);
        entity.setStatus("ENABLED");

        assertEquals("r-1", entity.getId());
        assertEquals("RULE_001", entity.getRuleCode());
        assertEquals("预警路由", entity.getRuleName());
        assertEquals(100, entity.getPriority());
        assertEquals("SMS", entity.getTargetChannel());
        assertEquals("EMAIL", entity.getFallbackChannel());
        assertEquals(1, entity.getSortOrder());
        assertEquals("ENABLED", entity.getStatus());
        assertNotNull(entity);
    }
}
