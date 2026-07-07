package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgNotificationDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgNotificationDOTest {

    @Test
    void testGetterSetter() {
        MsgNotificationDO entity = new MsgNotificationDO();
        entity.setId("n-1");
        entity.setTitle("测试通知");
        entity.setLevel("INFO");
        entity.setReadStatus(0);
        entity.setReadTime(LocalDateTime.now());
        entity.setRecallStatus("NONE");

        assertEquals("n-1", entity.getId());
        assertEquals("测试通知", entity.getTitle());
        assertEquals("INFO", entity.getLevel());
        assertEquals(0, entity.getReadStatus());
        assertNotNull(entity.getReadTime());
        assertEquals("NONE", entity.getRecallStatus());
        assertNotNull(entity);
    }
}
