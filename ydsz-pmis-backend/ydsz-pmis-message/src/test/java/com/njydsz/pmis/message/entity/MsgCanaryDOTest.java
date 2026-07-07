package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgCanaryDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgCanaryDOTest {

    @Test
    void testGetterSetter() {
        MsgCanaryDO entity = new MsgCanaryDO();
        entity.setId("c-1");
        entity.setCanaryKey("TPL_001");
        entity.setBucketTotal(100);
        entity.setPercentage(10);
        entity.setStatus("ENABLED");

        assertEquals("c-1", entity.getId());
        assertEquals("TPL_001", entity.getCanaryKey());
        assertEquals(100, entity.getBucketTotal());
        assertEquals(10, entity.getPercentage());
        assertEquals("ENABLED", entity.getStatus());
        assertNotNull(entity);
    }
}
