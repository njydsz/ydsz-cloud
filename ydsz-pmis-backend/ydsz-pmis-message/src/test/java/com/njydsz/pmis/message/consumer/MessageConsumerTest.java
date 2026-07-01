package com.njydsz.pmis.message.consumer;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MessageConsumer 单元测试（不依赖 RocketMQ 启动）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageConsumer 消息消费测试")
class MessageConsumerTest {

    private MessageService messageService;
    private MessageConsumer consumer;

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        consumer = new MessageConsumer(messageService);
    }

    @Test
    @DisplayName("onMessage 正常消息体 → 调 service.send")
    void onMessage_normal() {
        MessageRequest req = new MessageRequest();
        req.setChannel("EMAIL");
        req.setTemplateCode("TPL-001");
        req.setReceiver("test@ydsz-pmis.cn");
        req.setParams(new HashMap<>());
        req.setSubject("测试");
        req.setBizType("OPPORTUNITY");
        req.setBizId("OPP-001");

        String body = com.alibaba.fastjson2.JSON.toJSONString(req);
        consumer.onMessage(body);

        verify(messageService, times(1)).send(any(MessageRequest.class));
    }

    @Test
    @DisplayName("onMessage 空 body → 跳过，不调 service")
    void onMessage_empty() {
        consumer.onMessage(null);
        consumer.onMessage("");
        consumer.onMessage("   ");
        verifyNoInteractions(messageService);
    }

    @Test
    @DisplayName("onMessage 业务异常 → 捕获，不重试")
    void onMessage_bizException() {
        org.mockito.Mockito.doThrow(new BizException(400, "模板不存在"))
                .when(messageService).send(any(MessageRequest.class));

        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setTemplateCode("NOT-EXIST");
        String body = com.alibaba.fastjson2.JSON.toJSONString(req);

        // 业务异常被吞掉，不抛出
        consumer.onMessage(body);
        verify(messageService, times(1)).send(any(MessageRequest.class));
    }

    @Test
    @DisplayName("onMessage 系统异常 → 抛出，触发 RocketMQ 重投")
    void onMessage_systemException_throws() {
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(messageService).send(any(MessageRequest.class));

        MessageRequest req = new MessageRequest();
        req.setChannel("EMAIL");
        req.setTemplateCode("TPL-001");
        String body = com.alibaba.fastjson2.JSON.toJSONString(req);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.onMessage(body))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MessageConsumer failed");
        verify(messageService, times(1)).send(any(MessageRequest.class));
    }

    @Test
    @DisplayName("onMessage 非法 JSON → 解析为 null，跳过")
    void onMessage_invalidJson() {
        consumer.onMessage("not a json");
        verifyNoInteractions(messageService);
    }
}
