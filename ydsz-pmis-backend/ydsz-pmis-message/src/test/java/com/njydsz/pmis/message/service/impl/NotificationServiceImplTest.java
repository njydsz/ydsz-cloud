package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.NotificationSendDTO;
import com.njydsz.pmis.message.entity.MsgNotificationDO;
import com.njydsz.pmis.message.mapper.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.RecallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NotificationServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("NotificationServiceImpl 通知服务测试")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class NotificationServiceImplTest {

    @Mock
    private MsgNotificationMapper msgNotificationMapper;
    @Mock
    private RealtimePushService realtimePushService;
    @Mock
    private RecallService recallService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("send 批量接收人逐人入库并推送")
    void sendShouldInsertForEachReceiver() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setContent("c");
        dto.setReceiverIds(List.of("u1", "u2"));

        int count = notificationService.send(dto);

        assertEquals(2, count);
        verify(msgNotificationMapper, times(2)).insert(any(MsgNotificationDO.class));
        verify(realtimePushService).pushToUserWithOffline(eq("u1"), anyString(), any());
        verify(realtimePushService).pushToUserWithOffline(eq("u2"), anyString(), any());
    }

    @Test
    @DisplayName("send 单接收人(receiverId)入库")
    void sendShouldInsertForSingleReceiver() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setContent("c");
        dto.setReceiverId("u1");

        int count = notificationService.send(dto);

        assertEquals(1, count);
    }

    @Test
    @DisplayName("inbox 分页查询")
    void inboxShouldReturnPage() {
        NotificationQueryDTO query = new NotificationQueryDTO();
        query.setPage(1);
        query.setSize(10);
        Page<MsgNotificationDO> mockPage = new Page<>();
        when(msgNotificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgNotificationDO> result = notificationService.inbox("u1", query);
        assertEquals(mockPage, result);
    }

    @Test
    @DisplayName("markRead 委托 mapper")
    void markReadShouldDelegate() {
        when(msgNotificationMapper.markRead("n1", "u1")).thenReturn(1);
        assertEquals(true, notificationService.markRead("u1", "n1"));
    }

    @Test
    @DisplayName("countUnread 返回未读数")
    void countUnreadShouldReturnCount() {
        when(msgNotificationMapper.countUnread("u1")).thenReturn(5L);
        assertEquals(5L, notificationService.countUnread("u1"));
    }

    @Test
    @DisplayName("recall 委托 RecallService")
    void recallShouldDelegate() {
        when(recallService.recallNotification("u1", "n1")).thenReturn(true);
        assertEquals(true, notificationService.recall("u1", "n1"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("P2-7: send 时 tenantId 从 TenantContext 自动填充")
    void sendShouldSetTenantIdFromContext() {
        TenantContext.setTenantId("99");
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setContent("c");
        dto.setReceiverId("u1");

        notificationService.send(dto);

        ArgumentCaptor<MsgNotificationDO> captor = ArgumentCaptor.forClass(MsgNotificationDO.class);
        verify(msgNotificationMapper).insert(captor.capture());
        MsgNotificationDO inserted = captor.getValue();
        assertNotNull(inserted.getTenantId(), "tenantId 不应为 null");
        assertEquals("99", inserted.getTenantId(), "tenantId 应从 TenantContext 获取");
    }

    @Test
    @DisplayName("P2-7: 未设置 TenantContext 时 tenantId 默认为 1")
    void sendShouldUseDefaultTenantIdWhenContextEmpty() {
        // 不设置 TenantContext, 应使用默认值 "1"
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("t");
        dto.setContent("c");
        dto.setReceiverId("u1");

        notificationService.send(dto);

        ArgumentCaptor<MsgNotificationDO> captor = ArgumentCaptor.forClass(MsgNotificationDO.class);
        verify(msgNotificationMapper).insert(captor.capture());
        assertEquals(TenantContext.DEFAULT_TENANT_ID, captor.getValue().getTenantId());
    }
}
