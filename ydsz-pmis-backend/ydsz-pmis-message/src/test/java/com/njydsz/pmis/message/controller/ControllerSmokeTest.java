package com.njydsz.pmis.message.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.dto.CanaryUpsertDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.dto.NotificationSendDTO;
import com.njydsz.pmis.message.dto.PreferenceUpsertDTO;
import com.njydsz.pmis.message.dto.ReceiptCallbackDTO;
import com.njydsz.pmis.message.dto.RecallRequestDTO;
import com.njydsz.pmis.message.dto.RouteRuleUpsertDTO;
import com.njydsz.pmis.message.dto.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.dto.TemplateAuditDTO;
import com.njydsz.pmis.message.dto.TemplateCreateDTO;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.NotificationService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RecallService;
import com.njydsz.pmis.message.service.ReceiptService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 路由烟雾测试。
 *
 * <p>使用 standalone MockMvc 验证各 Controller 关键端点路由存在(非 404),
 * 不加载完整 Spring 上下文,避免依赖外部通道/配置 Bean。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("Controller 路由烟雾测试")
class ControllerSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockMvc build(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    @DisplayName("TemplateController")
    class TemplateSmoke {
        @Test
        void routesExist() throws Exception {
            TemplateService svc = mock(TemplateService.class);
            when(svc.page(any())).thenReturn(new Page<>());
            when(svc.getById(any())).thenReturn(new MsgTemplateDO());
            MockMvc m = build(new TemplateController(svc));
            m.perform(get("/message/template/page")).andExpect(status().isOk());
            m.perform(get("/message/template/{id}", "1")).andExpect(status().isOk());
            m.perform(post("/message/template").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new TemplateCreateDTO()))).andExpect(status().isOk());
            m.perform(put("/message/template/{id}", "1").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new TemplateCreateDTO()))).andExpect(status().isOk());
            m.perform(post("/message/template/{id}/audit", "1").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new TemplateAuditDTO()))).andExpect(status().isOk());
            m.perform(delete("/message/template/{id}", "1")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("MessageController")
    class MessageSmoke {
        @Test
        void routesExist() throws Exception {
            MessageService svc = mock(MessageService.class);
            @SuppressWarnings("unchecked")
            org.springframework.beans.factory.ObjectProvider<com.njydsz.pmis.message.producer.RocketMQMessageProducer> producerProvider =
                    mock(org.springframework.beans.factory.ObjectProvider.class);
            when(svc.send(any())).thenReturn(MessageResult.ok("SMS", "t"));
            when(svc.pageLog(any())).thenReturn(new Page<>());
            MockMvc m = build(new MessageController(svc, producerProvider));
            m.perform(post("/message/send").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new MessageRequest()))).andExpect(status().isOk());
            m.perform(post("/message/send-direct").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new MessageSendDTO()))).andExpect(status().isOk());
            m.perform(get("/message/log/page")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("NotificationController")
    class NotificationSmoke {
        @Test
        void routesExist() throws Exception {
            NotificationService notif = mock(NotificationService.class);
            RecallService recall = mock(RecallService.class);
            RealtimePushService push = mock(RealtimePushService.class);
            when(notif.inbox(any(), any())).thenReturn(new Page<>());
            when(notif.countUnread(any())).thenReturn(0L);
            when(notif.send(any())).thenReturn(1);
            MockMvc m = build(new NotificationController(notif, recall, push));
            m.perform(get("/notifications/inbox")).andExpect(status().isOk());
            m.perform(get("/notifications/unread-count")).andExpect(status().isOk());
            m.perform(post("/notifications/send").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new NotificationSendDTO()))).andExpect(status().isOk());
            m.perform(post("/notifications/broadcast").contentType("application/json").content("{}"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PreferenceController")
    class PreferenceSmoke {
        @Test
        void routesExist() throws Exception {
            PreferenceService svc = mock(PreferenceService.class);
            when(svc.listByUser(any())).thenReturn(List.of());
            when(svc.getByUser(any(), any(), any())).thenReturn(new MsgPreferenceDO());
            MockMvc m = build(new PreferenceController(svc));
            m.perform(get("/message/preference/{userId}", "u1")).andExpect(status().isOk());
            m.perform(get("/message/preference/{userId}/{channel}/{bizType}", "u1", "SMS", "ALERT"))
                    .andExpect(status().isOk());
            m.perform(post("/message/preference").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new PreferenceUpsertDTO()))).andExpect(status().isOk());
            m.perform(delete("/message/preference/{id}", "1")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("SubscriptionController")
    class SubscriptionSmoke {
        @Test
        void routesExist() throws Exception {
            SubscriptionService svc = mock(SubscriptionService.class);
            when(svc.listByUser(any())).thenReturn(List.of());
            when(svc.listByTopic(any(), any())).thenReturn(List.of());
            MockMvc m = build(new SubscriptionController(svc));
            m.perform(get("/message/subscription/user/{userId}", "u1")).andExpect(status().isOk());
            m.perform(get("/message/subscription/topic/{topicCode}/{channel}", "RISK", "SMS"))
                    .andExpect(status().isOk());
            m.perform(post("/message/subscription").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new SubscriptionUpsertDTO()))).andExpect(status().isOk());
            m.perform(post("/message/subscription/unsubscribe").param("userId", "u1")
                    .param("topicCode", "RISK").param("channel", "SMS")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("RouteRuleController")
    class RouteRuleSmoke {
        @Test
        void routesExist() throws Exception {
            RouteRuleService svc = mock(RouteRuleService.class);
            when(svc.page(any())).thenReturn(new Page<>());
            when(svc.getById(any())).thenReturn(new MsgRouteRuleDO());
            when(svc.listEnabled()).thenReturn(List.of());
            MockMvc m = build(new RouteRuleController(svc));
            m.perform(get("/message/route-rule/page")).andExpect(status().isOk());
            m.perform(get("/message/route-rule/enabled")).andExpect(status().isOk());
            m.perform(get("/message/route-rule/{id}", "1")).andExpect(status().isOk());
            m.perform(post("/message/route-rule").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new RouteRuleUpsertDTO()))).andExpect(status().isOk());
            m.perform(delete("/message/route-rule/{id}", "1")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("ReceiptController")
    class ReceiptSmoke {
        @Test
        void routesExist() throws Exception {
            ReceiptService svc = mock(ReceiptService.class);
            when(svc.listByLogId(any())).thenReturn(List.of());
            MockMvc m = build(new ReceiptController(svc));
            m.perform(get("/message/receipt/{logId}", "1")).andExpect(status().isOk());
            m.perform(post("/message/receipt/callback").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new ReceiptCallbackDTO()))).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("RecallController")
    class RecallSmoke {
        @Test
        void routesExist() throws Exception {
            RecallService svc = mock(RecallService.class);
            when(svc.recallMessage(any())).thenReturn(true);
            when(svc.recallBatch(any(), any())).thenReturn(0);
            MockMvc m = build(new RecallController(svc));
            m.perform(post("/message/recall/message/{logId}", "1")).andExpect(status().isOk());
            m.perform(post("/message/recall/batch").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new RecallRequestDTO()))).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("AggregateController")
    class AggregateSmoke {
        @Test
        void routesExist() throws Exception {
            AggregateService svc = mock(AggregateService.class);
            when(svc.page(any())).thenReturn(new Page<MsgAggregateDO>());
            when(svc.flushDue()).thenReturn(0);
            when(svc.flushByGroup(any(), any())).thenReturn(0);
            MockMvc m = build(new AggregateController(svc));
            m.perform(get("/message/aggregate/page")).andExpect(status().isOk());
            m.perform(post("/message/aggregate/flush").param("group", "RISK").param("receiver", "u1"))
                    .andExpect(status().isOk());
            m.perform(post("/message/aggregate/flush-due")).andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CanaryController")
    class CanarySmoke {
        @Test
        void routesExist() throws Exception {
            CanaryService svc = mock(CanaryService.class);
            when(svc.getByKey(any())).thenReturn(new MsgCanaryDO());
            when(svc.page(any())).thenReturn(new Page<>());
            when(svc.hit(any(), any())).thenReturn(false);
            MockMvc m = build(new CanaryController(svc));
            m.perform(get("/message/canary/{canaryKey}", "TPL")).andExpect(status().isOk());
            m.perform(get("/message/canary/page")).andExpect(status().isOk());
            m.perform(get("/message/canary/hit").param("canaryKey", "TPL").param("bucketValue", "v1"))
                    .andExpect(status().isOk());
            m.perform(post("/message/canary").contentType("application/json")
                    .content(MAPPER.writeValueAsString(new CanaryUpsertDTO()))).andExpect(status().isOk());
        }
    }
}
