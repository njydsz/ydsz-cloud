package com.njydsz.pmis.message.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * DTO 烟雾测试:校验所有 DTO 可实例化(Lombok @Data 生成无参构造)。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class DtoSmokeTest {

    @Test
    void testTemplateCreateDTO() {
        assertNotNull(new TemplateCreateDTO());
    }

    @Test
    void testTemplateAuditDTO() {
        assertNotNull(new TemplateAuditDTO());
    }

    @Test
    void testTemplateQueryDTO() {
        TemplateQueryDTO dto = new TemplateQueryDTO();
        dto.setTemplateCode("TPL_001");
        assertNotNull(dto);
        assertNotNull(dto.getTemplateCode());
    }

    @Test
    void testMessageSendDTO() {
        assertNotNull(new MessageSendDTO());
    }

    @Test
    void testMessageLogQueryDTO() {
        MessageLogQueryDTO dto = new MessageLogQueryDTO();
        dto.setChannel("SMS");
        assertNotNull(dto);
        assertNotNull(dto.getChannel());
    }

    @Test
    void testNotificationSendDTO() {
        assertNotNull(new NotificationSendDTO());
    }

    @Test
    void testNotificationQueryDTO() {
        NotificationQueryDTO dto = new NotificationQueryDTO();
        dto.setReadStatus(0);
        assertNotNull(dto);
        assertNotNull(dto.getReadStatus());
    }

    @Test
    void testPreferenceUpsertDTO() {
        assertNotNull(new PreferenceUpsertDTO());
    }

    @Test
    void testSubscriptionUpsertDTO() {
        assertNotNull(new SubscriptionUpsertDTO());
    }

    @Test
    void testRouteRuleUpsertDTO() {
        assertNotNull(new RouteRuleUpsertDTO());
    }

    @Test
    void testReceiptCallbackDTO() {
        assertNotNull(new ReceiptCallbackDTO());
    }

    @Test
    void testRecallRequestDTO() {
        assertNotNull(new RecallRequestDTO());
    }

    @Test
    void testCanaryUpsertDTO() {
        assertNotNull(new CanaryUpsertDTO());
    }
}
