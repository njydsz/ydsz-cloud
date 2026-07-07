package com.njydsz.pmis.message.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgReceiptDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgReceiptDOTest {

    @Test
    void testGetterSetter() {
        MsgReceiptDO entity = new MsgReceiptDO();
        entity.setId("rc-1");
        entity.setLogId("log-1");
        entity.setReceiptType("DELIVERED");
        entity.setReceiptTime(LocalDateTime.now());
        entity.setProviderCode("aliyun");

        assertEquals("rc-1", entity.getId());
        assertEquals("log-1", entity.getLogId());
        assertEquals("DELIVERED", entity.getReceiptType());
        assertNotNull(entity.getReceiptTime());
        assertEquals("aliyun", entity.getProviderCode());
        assertNotNull(entity);
    }
}
