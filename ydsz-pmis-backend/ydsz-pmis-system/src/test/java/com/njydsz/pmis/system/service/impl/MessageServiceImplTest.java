package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.system.channel.MessageChannel;
import com.njydsz.pmis.system.entity.MessageLogDO;
import com.njydsz.pmis.system.entity.MessageTemplateDO;
import com.njydsz.pmis.system.mapper.MessageLogMapper;
import com.njydsz.pmis.system.mapper.MessageTemplateMapper;
import com.njydsz.pmis.system.service.MessageServiceImpl;
import com.njydsz.pmis.system.template.TemplateEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl 单元测试")
class MessageServiceImplTest {

    @Mock
    private MessageLogMapper messageLogMapper;

    @Mock
    private MessageTemplateMapper messageTemplateMapper;

    @Mock
    private TemplateEngine templateEngine;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private MessageChannel mockChannel;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Nested
    @DisplayName("send 方法")
    class SendTest {

        @Test
        @DisplayName("request 为 null 时应抛出异常")
        void shouldThrowWhenRequestIsNull() {
            assertThatThrownBy(() -> messageService.send(null))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_d9712a58");
        }

        @Test
        @DisplayName("channel 为空时应抛出异常")
        void shouldThrowWhenChannelIsEmpty() {
            MessageRequest request = new MessageRequest();
            request.setChannel("");
            request.setReceiver("test@test.com");
            request.setContent("test content");

            assertThatThrownBy(() -> messageService.send(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_fd9fba6f");
        }

        @Test
        @DisplayName("receiver 为空时应抛出异常")
        void shouldThrowWhenReceiverIsEmpty() {
            MessageRequest request = new MessageRequest();
            request.setChannel("EMAIL");
            request.setReceiver("");

            assertThatThrownBy(() -> messageService.send(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_35f5875c");
        }

        @Test
        @DisplayName("content 为空时应抛出异常")
        void shouldThrowWhenContentIsEmpty() {
            // 手动注入 channel 到缓存中
            when(applicationContext.getBeansOfType(MessageChannel.class)).thenReturn(Map.of());
            messageService.initChannels();
            getChannelCache(messageService).put("EMAIL", mockChannel);

            MessageRequest request = new MessageRequest();
            request.setChannel("EMAIL");
            request.setReceiver("test@test.com");
            request.setContent("");

            assertThatThrownBy(() -> messageService.send(request))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.message.msg_48e93db8");
        }
    }

    @Nested
    @DisplayName("sendDirect 方法")
    class SendDirectTest {

        @Test
        @DisplayName("sendDirect 应清除 templateCode 后调用 send")
        void shouldClearTemplateCode() {
            Map<String, MessageChannel> channelCache = getChannelCache(messageService);
            channelCache.put("EMAIL", mockChannel);
            when(mockChannel.channelType()).thenReturn("EMAIL");
            when(mockChannel.send(any(MessageRequest.class))).thenReturn(MessageResult.ok("EMAIL", "trace123"));
            when(messageLogMapper.insert(any(MessageLogDO.class))).thenReturn(1);

            MessageRequest request = new MessageRequest();
            request.setChannel("EMAIL");
            request.setReceiver("test@test.com");
            request.setContent("direct content");
            request.setTemplateCode("IGNORE_ME");

            MessageResult result = messageService.sendDirect(request);

            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("pageLog 方法")
    class PageLogTest {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("分页查询应返回正确结果")
        void shouldReturnPagedLogs() {
            when(messageLogMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<MessageLogDO> result = messageService.pageLog(1, 10, "EMAIL", null, null);

            assertThat(result).isNotNull();
            verify(messageLogMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("loadTemplate 方法")
    class LoadTemplateTest {

        @Test
        @DisplayName("模板存在时应返回模板")
        void shouldReturnTemplateWhenExists() {
            MessageTemplateDO template = new MessageTemplateDO();
            template.setId(1L);
            template.setTemplateCode("WELCOME");
            when(messageTemplateMapper.selectByCodeAndChannel("WELCOME", "EMAIL", 1L))
                    .thenReturn(template);

            MessageTemplateDO result = messageService.loadTemplate("WELCOME", "EMAIL", 1L);

            assertThat(result).isNotNull();
            assertThat(result.getTemplateCode()).isEqualTo("WELCOME");
        }

        @Test
        @DisplayName("参数为空时应返回 null")
        void shouldReturnNullWhenParamsAreEmpty() {
            assertThat(messageService.loadTemplate(null, "EMAIL", null)).isNull();
            assertThat(messageService.loadTemplate("WELCOME", "", null)).isNull();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, MessageChannel> getChannelCache(MessageServiceImpl service) {
        try {
            java.lang.reflect.Field field = MessageServiceImpl.class.getDeclaredField("channelCache");
            field.setAccessible(true);
            return (Map<String, MessageChannel>) field.get(service);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}