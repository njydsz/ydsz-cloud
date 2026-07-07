package com.njydsz.pmis.message.entity;

import com.njydsz.pmis.common.entity.BaseDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MsgTemplateDO 实体 getter/setter 烟雾测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class MsgTemplateDOTest {

    @Test
    void testGetterSetterAndInheritance() {
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setId("123");
        entity.setTemplateCode("TPL_001");
        entity.setChannel("SMS");
        entity.setAuditStatus("APPROVED");
        entity.setAuditAt(LocalDateTime.now());

        assertEquals("123", entity.getId());
        assertEquals("TPL_001", entity.getTemplateCode());
        assertEquals("SMS", entity.getChannel());
        assertEquals("APPROVED", entity.getAuditStatus());
        assertNotNull(entity.getAuditAt());
        assertNotNull(entity);
        // 继承 BaseDO 审计字段可访问
        entity.setCreatedBy("SYSTEM");
        assertEquals("SYSTEM", ((BaseDO) entity).getCreatedBy());
    }
}
