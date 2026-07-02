package com.njydsz.pmis.notification.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.notification.dto.NotificationQueryDTO;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;
import com.njydsz.pmis.notification.feign.MessageServiceClient;
import com.njydsz.pmis.notification.feign.UserServiceClient;
import com.njydsz.pmis.notification.mapper.NotificationMapper;
import com.njydsz.pmis.notification.service.NotificationService;
import com.njydsz.pmis.notification.service.NotificationService.EmailDispatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("NotificationServiceImpl 通知服务测试")
class NotificationServiceImplTest {

    /** 通知 Mapper（Mock） */
    private NotificationMapper mapper;
    /** 消息服务 Feign 客户端（Mock） */
    private MessageServiceClient messageClient;
    /** 用户服务 Feign 客户端（Mock） */
    private UserServiceClient userClient;
    /** 待测服务实例 */
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationMapper.class);
        messageClient = mock(MessageServiceClient.class);
        userClient = mock(UserServiceClient.class);
        service = new NotificationServiceImpl(mapper, messageClient, userClient);
    }

    @Test
    @DisplayName("send 单接收应插入一条")
    void send_single() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("hello");
        dto.setReceiverId(10L);

        int n = service.send(dto);
        assertThat(n).isEqualTo(1);
        verify(mapper, times(1)).insert(any(NotificationDO.class));
    }

    @Test
    @DisplayName("send 批量应插入多条")
    void send_batch() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("hello");
        dto.setReceiverIds(List.of(1L, 2L, 3L));

        int n = service.send(dto);
        assertThat(n).isEqualTo(3);
        verify(mapper, times(3)).insert(any(NotificationDO.class));
    }

    @Test
    @DisplayName("send 接收人为空应抛 BAD_REQUEST")
    void send_empty() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("hello");
        assertThatThrownBy(() -> service.send(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("send 应设置默认 level/category")
    void send_defaults() {
        when(mapper.insert(any(NotificationDO.class))).thenAnswer(inv -> {
            NotificationDO n = inv.getArgument(0);
            assertThat(n.getLevel()).isEqualTo("INFO");
            assertThat(n.getCategory()).isEqualTo("SYSTEM");
            assertThat(n.getReadStatus()).isZero();
            return 1;
        });
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setReceiverId(1L);
        service.send(dto);
    }

    @Test
    @DisplayName("countUnread 应透传 Mapper")
    void countUnread() {
        when(mapper.countUnread(1L)).thenReturn(7L);
        assertThat(service.countUnread(1L)).isEqualTo(7L);
    }

    @Test
    @DisplayName("countUnread 返回 null 应转换为 0")
    void countUnread_nullSafe() {
        when(mapper.countUnread(1L)).thenReturn(null);
        assertThat(service.countUnread(1L)).isZero();
    }

    @Test
    @DisplayName("markRead 应返回 true/false")
    void markRead() {
        when(mapper.markRead(10L, 1L)).thenReturn(1);
        when(mapper.markRead(99L, 1L)).thenReturn(0);
        assertThat(service.markRead(1L, 10L)).isTrue();
        assertThat(service.markRead(1L, 99L)).isFalse();
    }

    @Test
    @DisplayName("markAllRead 应返回受影响行数")
    void markAllRead() {
        when(mapper.markAllRead(1L)).thenReturn(5);
        assertThat(service.markAllRead(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("delete 仅删除属于当前用户的")
    void delete_ownerCheck() {
        when(mapper.selectById(1L)).thenReturn(notif(1L, 10L));
        when(mapper.selectById(2L)).thenReturn(notif(2L, 99L));
        service.delete(10L, List.of(1L, 2L));
        verify(mapper, times(1)).deleteById(eq(1L));
        verify(mapper, times(0)).deleteById(eq(2L));
    }

    @Test
    @DisplayName("delete 空列表应直接返回")
    void delete_empty() {
        service.delete(1L, List.of());
        verify(mapper, times(0)).deleteById(any());
    }

    @Test
    @DisplayName("sendWithEmail 邮件关闭时只插站内, 不调用 message 服务")
    void sendWithEmail_disabled() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(false);

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.getInboxCount()).isEqualTo(1);
        assertThat(r.isEmailSent()).isFalse();
        verify(messageClient, never()).send(any());
    }

    @Test
    @DisplayName("sendWithEmail 邮件开启 + 直接传 email 应投递 EMAIL 通道")
    void sendWithEmail_directEmail() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        Map<String, Object> data = new HashMap<>();
        data.put("providerTraceId", "EMAIL-abcdef0123456789");
        when(messageClient.send(any(MessageServiceClient.MessageFeignDTO.class)))
                .thenReturn(Result.ok(data));

        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("欢迎");
        dto.setContent("Hello, world");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(true);
        dto.setReceiverEmail("a@x.com");

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.getInboxCount()).isEqualTo(1);
        assertThat(r.isEmailSent()).isTrue();
        assertThat(r.getProviderTraceId()).isEqualTo("EMAIL-abcdef0123456789");
        verify(messageClient, times(1)).send(any(MessageServiceClient.MessageFeignDTO.class));
    }

    @Test
    @DisplayName("sendWithEmail 邮件开启 + 未传 email 应通过 user 服务解析")
    void sendWithEmail_resolveFromUser() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        Map<String, Object> emp = new HashMap<>();
        emp.put("email", "user@x.com");
        when(userClient.getEmployee(10L)).thenReturn(Result.ok(emp));
        Map<String, Object> data = new HashMap<>();
        data.put("providerTraceId", "EMAIL-zzz");
        when(messageClient.send(any(MessageServiceClient.MessageFeignDTO.class)))
                .thenReturn(Result.ok(data));

        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("欢迎");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(true);

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.isEmailSent()).isTrue();
        verify(userClient, times(1)).getEmployee(eq(10L));
    }

    @Test
    @DisplayName("sendWithEmail 邮箱解析为空应跳过邮件, 错误信息明确")
    void sendWithEmail_emailEmpty() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        when(userClient.getEmployee(10L)).thenReturn(Result.ok(new HashMap<>()));

        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("欢迎");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(true);

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.isEmailSent()).isFalse();
        assertThat(r.getEmailError()).contains("接收人邮箱为空");
        verify(messageClient, never()).send(any());
    }

    @Test
    @DisplayName("sendWithEmail 邮件服务降级时不影响站内通知")
    void sendWithEmail_messageDown() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        when(messageClient.send(any(MessageServiceClient.MessageFeignDTO.class)))
                .thenThrow(new RuntimeException("connection refused"));

        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("欢迎");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(true);
        dto.setReceiverEmail("a@x.com");

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.getInboxCount()).isEqualTo(1);
        assertThat(r.isEmailSent()).isFalse();
        assertThat(r.getEmailError()).contains("RuntimeException");
    }

    @Test
    @DisplayName("sendWithEmail 邮件服务返回失败码应记录错误")
    void sendWithEmail_messageFailCode() {
        when(mapper.insert(any(NotificationDO.class))).thenReturn(1);
        when(messageClient.send(any(MessageServiceClient.MessageFeignDTO.class)))
                .thenReturn(Result.failed(10001, "模板不存在"));

        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("欢迎");
        dto.setReceiverId(10L);
        dto.setEmailEnabled(true);
        dto.setReceiverEmail("a@x.com");

        EmailDispatchResult r = service.sendWithEmail(dto);
        assertThat(r.isEmailSent()).isFalse();
        assertThat(r.getEmailError()).contains("模板不存在");
    }

    @Test
    @DisplayName("sendWithEmail 多接收人应抛 BAD_REQUEST")
    void sendWithEmail_multiReceiver() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setReceiverIds(List.of(1L, 2L));
        dto.setEmailEnabled(true);
        assertThatThrownBy(() -> service.sendWithEmail(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("inbox 应按 receiverId + category + level + readStatus 过滤")
    void inbox_filter() {
        when(mapper.selectPage(any(), any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());
        NotificationQueryDTO q = new NotificationQueryDTO();
        q.setPage(1);
        q.setSize(10);
        q.setCategory("ALERT");
        q.setLevel("WARN");
        q.setReadStatus(0);
        service.inbox(7L, q);
        verify(mapper, times(1)).selectPage(any(), any());
    }

    /**
     * 构造测试用通知实体
     *
     * @param id         通知 ID
     * @param receiverId 接收人 ID
     * @return 通知实体
     */
    private NotificationDO notif(Long id, Long receiverId) {
        NotificationDO n = new NotificationDO();
        n.setId(id);
        n.setReceiverId(receiverId);
        n.setTitle("t");
        n.setReadStatus(0);
        return n;
    }
}
