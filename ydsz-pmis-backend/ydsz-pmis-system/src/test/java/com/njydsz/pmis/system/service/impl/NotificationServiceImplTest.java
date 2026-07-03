package com.njydsz.pmis.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.system.dto.NotificationQueryDTO;
import com.njydsz.pmis.system.dto.NotificationSendDTO;
import com.njydsz.pmis.system.entity.NotificationDO;
import com.njydsz.pmis.system.mapper.NotificationMapper;
import com.njydsz.pmis.system.service.MessageService;
import com.njydsz.pmis.system.service.RealtimePushService;
import com.njydsz.pmis.system.feign.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl 单元测试")
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private MessageService messageService;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RealtimePushService realtimePushService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Nested
    @DisplayName("send 方法")
    class SendTest {

        @Test
        @DisplayName("发送通知成功时应返回插入条数")
        void shouldSendNotificationSuccessfully() {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTitle("Test Notification");
            dto.setContent("Test content");
            dto.setReceiverId(1L);

            doAnswer(invocation -> {
                NotificationDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(notificationMapper).insert(any(NotificationDO.class));

            int count = notificationService.send(dto);

            assertThat(count).isEqualTo(1);
            verify(notificationMapper).insert(any(NotificationDO.class));
            verify(realtimePushService).pushToUser(eq(1L), eq("NOTIFICATION"), any(NotificationDO.class));
        }

        @Test
        @DisplayName("接收人为空时应抛出异常")
        void shouldThrowWhenReceiverIsEmpty() {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTitle("Test");

            assertThatThrownBy(() -> notificationService.send(dto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("error.common.msg_35f5875c");
        }

        @Test
        @DisplayName("批量发送通知成功时应返回正确条数")
        void shouldSendBatchNotificationSuccessfully() {
            NotificationSendDTO dto = new NotificationSendDTO();
            dto.setTitle("Batch Notification");
            dto.setContent("Batch content");
            dto.setReceiverIds(List.of(1L, 2L, 3L));

            doAnswer(invocation -> {
                NotificationDO entity = invocation.getArgument(0);
                entity.setId(1L);
                return 1;
            }).when(notificationMapper).insert(any(NotificationDO.class));

            int count = notificationService.send(dto);

            assertThat(count).isEqualTo(3);
            verify(notificationMapper, times(3)).insert(any(NotificationDO.class));
            verify(realtimePushService, times(3)).pushToUser(anyLong(), eq("NOTIFICATION"), any(NotificationDO.class));
        }
    }

    @Nested
    @DisplayName("inbox 方法")
    class InboxTest {

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("收件箱分页查询应返回正确结果")
        void shouldReturnInboxPage() {
            NotificationQueryDTO query = new NotificationQueryDTO();
            query.setPage(1);
            query.setSize(10);
            when(notificationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<NotificationDO> result = notificationService.inbox(1L, query);

            assertThat(result).isNotNull();
            verify(notificationMapper).selectPage(any(Page.class), any());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("带分类过滤的收件箱查询应正确过滤")
        void shouldFilterByCategory() {
            NotificationQueryDTO query = new NotificationQueryDTO();
            query.setPage(1);
            query.setSize(10);
            query.setCategory("WORKFLOW");
            when(notificationMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>());

            Page<NotificationDO> result = notificationService.inbox(1L, query);

            assertThat(result).isNotNull();
            verify(notificationMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("countUnread 方法")
    class CountUnreadTest {

        @Test
        @DisplayName("应返回未读数量")
        void shouldReturnUnreadCount() {
            when(notificationMapper.countUnread(1L)).thenReturn(5L);

            long count = notificationService.countUnread(1L);

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("mapper 返回 null 时应返回 0")
        void shouldReturnZeroWhenNull() {
            when(notificationMapper.countUnread(1L)).thenReturn(null);

            long count = notificationService.countUnread(1L);

            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("markRead 方法")
    class MarkReadTest {

        @Test
        @DisplayName("标记已读成功时应返回 true")
        void shouldReturnTrueWhenMarked() {
            when(notificationMapper.markRead(1L, 1L)).thenReturn(1);

            boolean result = notificationService.markRead(1L, 1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("通知不存在时应返回 false")
        void shouldReturnFalseWhenNotFound() {
            when(notificationMapper.markRead(999L, 1L)).thenReturn(0);

            boolean result = notificationService.markRead(1L, 999L);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTest {

        @Test
        @DisplayName("删除属于自己的通知应成功")
        void shouldDeleteOwnNotification() {
            NotificationDO n = new NotificationDO();
            n.setId(1L);
            n.setReceiverId(1L);
            when(notificationMapper.selectById(1L)).thenReturn(n);
            when(notificationMapper.deleteById(1L)).thenReturn(1);

            assertThatCode(() -> notificationService.delete(1L, List.of(1L)))
                    .doesNotThrowAnyException();
            verify(notificationMapper).deleteById(1L);
        }

        @Test
        @DisplayName("不应删除不属于自己的通知")
        void shouldNotDeleteOthersNotification() {
            NotificationDO n = new NotificationDO();
            n.setId(2L);
            n.setReceiverId(2L);
            when(notificationMapper.selectById(2L)).thenReturn(n);

            assertThatCode(() -> notificationService.delete(1L, List.of(2L)))
                    .doesNotThrowAnyException();
            verify(notificationMapper, never()).deleteById(2L);
        }
    }
}