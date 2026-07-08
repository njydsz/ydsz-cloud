package com.njydsz.pmis.message.producer;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.service.TemplateService;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link MessageTransactionListener} 单元测试（P2-3 事务消息）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageTransactionListener 事务消息监听器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageTransactionListenerTest {

    @Mock
    private TemplateService templateService;
    @Mock
    private ChannelRouter channelRouter;

    @InjectMocks
    private MessageTransactionListener listener;

    private MessageRequest buildValidRequest() {
        MessageRequest req = new MessageRequest();
        req.setMessageId("msg-001");
        req.setChannel("SMS");
        req.setTemplateCode("TPL_WELCOME");
        req.setReceiver("13800138000");
        req.setBizType("WELCOME");
        req.setBizId("b1");
        return req;
    }

    private MsgTemplateDO buildEnabledTemplate() {
        MsgTemplateDO tpl = new MsgTemplateDO();
        tpl.setTemplateCode("TPL_WELCOME");
        tpl.setStatus("ENABLED");
        tpl.setContent("hello ${name}");
        return tpl;
    }

    @Test
    @DisplayName("executeLocalTransaction: 校验通过 → COMMIT")
    void executeLocalTransactionShouldCommitWhenValid() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(true);
        when(templateService.loadByCodeAndChannel(eq("TPL_WELCOME"), eq("SMS"), any(), any()))
                .thenReturn(buildEnabledTemplate());

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.COMMIT, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: 通道未启用 → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenChannelDisabled() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(false);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: 模板不存在 → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenTemplateNotFound() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(true);
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), any())).thenReturn(null);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: 模板未启用 → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenTemplateDisabled() {
        MessageRequest req = buildValidRequest();
        MsgTemplateDO tpl = buildEnabledTemplate();
        tpl.setStatus("DISABLED");
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(true);
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), any())).thenReturn(tpl);

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: arg 为 null → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenArgNull() {
        RocketMQLocalTransactionState state = listener.executeLocalTransaction(null, null);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: 接收人为空 → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenReceiverBlank() {
        MessageRequest req = buildValidRequest();
        req.setReceiver("");

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("executeLocalTransaction: 异常 → ROLLBACK")
    void executeLocalTransactionShouldRollbackWhenException() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenThrow(new RuntimeException("redis down"));

        RocketMQLocalTransactionState state = listener.executeLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build(), req);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }

    @Test
    @DisplayName("checkLocalTransaction: 校验通过 → COMMIT")
    void checkLocalTransactionShouldCommitWhenValid() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenReturn(true);
        when(templateService.loadByCodeAndChannel(anyString(), anyString(), any(), any()))
                .thenReturn(buildEnabledTemplate());

        RocketMQLocalTransactionState state = listener.checkLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build());

        assertEquals(RocketMQLocalTransactionState.COMMIT, state);
    }

    @Test
    @DisplayName("checkLocalTransaction: 异常 → UNKNOWN")
    void checkLocalTransactionShouldReturnUnknownWhenException() {
        MessageRequest req = buildValidRequest();
        when(channelRouter.isChannelEnabled("SMS")).thenThrow(new RuntimeException("redis down"));

        RocketMQLocalTransactionState state = listener.checkLocalTransaction(
                MessageBuilder.withPayload(JsonUtils.toJson(req)).build());

        assertEquals(RocketMQLocalTransactionState.UNKNOWN, state);
    }

    @Test
    @DisplayName("checkLocalTransaction: 无法解析 → ROLLBACK")
    void checkLocalTransactionShouldRollbackWhenUnresolvable() {
        Message<String> msg = MessageBuilder.withPayload("").build();

        RocketMQLocalTransactionState state = listener.checkLocalTransaction(msg);

        assertEquals(RocketMQLocalTransactionState.ROLLBACK, state);
    }
}
