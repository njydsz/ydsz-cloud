package com.njydsz.pmis.notification.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.notification.dto.NotificationSendDTO;
import com.njydsz.pmis.notification.entity.NotificationDO;
import com.njydsz.pmis.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationServiceImpl 单元测试
 */
@DisplayName("NotificationServiceImpl 通知服务测试")
class NotificationServiceImplTest {

    private NotificationMapper mapper;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(NotificationMapper.class);
        service = new NotificationServiceImpl(mapper);
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
        when(mapper.selectCount(any())).thenReturn(7L);
        assertThat(service.countUnread(1L)).isEqualTo(7L);
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

    private NotificationDO notif(Long id, Long receiverId) {
        NotificationDO n = new NotificationDO();
        n.setId(id);
        n.setReceiverId(receiverId);
        n.setTitle("t");
        n.setReadStatus(0);
        return n;
    }
}
