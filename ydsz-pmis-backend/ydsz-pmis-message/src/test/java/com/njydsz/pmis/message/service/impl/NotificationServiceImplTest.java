package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.core.NotificationQueryDTO;
import com.njydsz.pmis.message.dto.core.NotificationSendDTO;
import com.njydsz.pmis.message.entity.core.MsgNotificationDO;
import com.njydsz.pmis.message.enums.receipt.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.impl.core.NotificationServiceImpl;
import com.njydsz.pmis.message.service.receipt.RecallService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知服务单元测试。
 *
 * <p>覆盖单发/群发通知、收件箱分页、未读计数、已读标记、删除（权限校验）、撤回委托。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("通知服务 NotificationServiceImpl 单元测试")
class NotificationServiceImplTest {

    @Mock
    private MsgNotificationMapper msgNotificationMapper;

    @Mock
    private RealtimePushService realtimePushService;

    @Mock
    private RecallService recallService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    // ==================== send ====================

    @Test
    @DisplayName("正常场景：单接收人发送通知")
    void 单接收人发送通知() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverId("u1");
        dto.setTitle("测试通知");
        dto.setContent("内容");
        dto.setLevel("WARN");
        dto.setCategory("ALERT");

        int count = notificationService.send(dto);

        assertEquals(1, count);
        ArgumentCaptor<MsgNotificationDO> captor = ArgumentCaptor.forClass(MsgNotificationDO.class);
        verify(msgNotificationMapper).insert(captor.capture());
        MsgNotificationDO saved = captor.getValue();
        assertEquals("u1", saved.getReceiverId());
        assertEquals("测试通知", saved.getTitle());
        assertEquals("WARN", saved.getLevel());
        assertEquals("ALERT", saved.getCategory());
        assertEquals(0, saved.getReadStatus());
        assertEquals(RecallStatusEnum.NONE.name(), saved.getRecallStatus());
        verify(realtimePushService).pushToUserWithOffline(eq("u1"), eq("NOTIFICATION"), any(MsgNotificationDO.class));
    }

    @Test
    @DisplayName("正常场景：多接收人群发通知")
    void 多接收人群发通知() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverIds(List.of("u1", "u2", "u3"));
        dto.setTitle("群发通知");

        int count = notificationService.send(dto);

        assertEquals(3, count);
        verify(msgNotificationMapper, times(3)).insert(any(MsgNotificationDO.class));
    }

    @Test
    @DisplayName("边界场景：receiverIds 为空但 receiverId 存在时使用 receiverId")
    void receiverIds为空使用receiverId() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverId("u1");
        dto.setTitle("单发");

        int count = notificationService.send(dto);

        assertEquals(1, count);
    }

    @Test
    @DisplayName("边界场景：level 为空时默认 INFO，category 为空时默认 SYSTEM")
    void level和category为空使用默认值() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverId("u1");
        dto.setTitle("通知");

        notificationService.send(dto);

        ArgumentCaptor<MsgNotificationDO> captor = ArgumentCaptor.forClass(MsgNotificationDO.class);
        verify(msgNotificationMapper).insert(captor.capture());
        assertEquals("INFO", captor.getValue().getLevel());
        assertEquals("SYSTEM", captor.getValue().getCategory());
    }

    @Test
    @DisplayName("边界场景：senderId 为空时使用 SYSTEM")
    void senderId为空使用System() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setReceiverId("u1");
        dto.setTitle("通知");

        notificationService.send(dto);

        ArgumentCaptor<MsgNotificationDO> captor = ArgumentCaptor.forClass(MsgNotificationDO.class);
        verify(msgNotificationMapper).insert(captor.capture());
        assertEquals(SystemConstants.SYSTEM_USER_ID, captor.getValue().getSenderId());
    }

    @Test
    @DisplayName("异常场景：dto 为空抛 BizException")
    void dto为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> notificationService.send(null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("异常场景：接收人为空抛 BizException")
    void 接收人为空抛异常() {
        NotificationSendDTO dto = new NotificationSendDTO();
        dto.setTitle("无接收人");

        BizException ex = assertThrows(BizException.class, () -> notificationService.send(dto));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== inbox ====================

    @Test
    @DisplayName("正常场景：分页查询收件箱")
    void 分页查询收件箱() {
        NotificationQueryDTO query = new NotificationQueryDTO();
        query.setPage(1);
        query.setSize(10);
        query.setCategory("SYSTEM");
        query.setReadStatus(0);
        Page<MsgNotificationDO> mockPage = new Page<>();
        when(msgNotificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgNotificationDO> result = notificationService.inbox("u1", query);

        assertNotNull(result);
        verify(msgNotificationMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("边界场景：query 为 null 时使用默认分页")
    void query为null使用默认分页() {
        Page<MsgNotificationDO> mockPage = new Page<>();
        when(msgNotificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        Page<MsgNotificationDO> result = notificationService.inbox("u1", null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("异常场景：userId 为空抛 BizException")
    void inboxUserId为空抛异常() {
        BizException ex = assertThrows(BizException.class, () -> notificationService.inbox(null, null));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ==================== countUnread ====================

    @Test
    @DisplayName("正常场景：统计未读通知数")
    void 统计未读通知数() {
        when(msgNotificationMapper.countUnread("u1")).thenReturn(5L);

        long count = notificationService.countUnread("u1");

        assertEquals(5L, count);
    }

    @Test
    @DisplayName("边界场景：mapper 返回 null 时返回 0")
    void mapper返回null时返回0() {
        when(msgNotificationMapper.countUnread("u1")).thenReturn(null);

        long count = notificationService.countUnread("u1");

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("边界场景：userId 为空时返回 0")
    void countUnreadUserId为空返回0() {
        long count = notificationService.countUnread(null);
        assertEquals(0L, count);
    }

    // ==================== markRead / markAllRead ====================

    @Test
    @DisplayName("正常场景：标记单条通知已读")
    void 标记单条已读() {
        when(msgNotificationMapper.markRead("n1", "u1")).thenReturn(1);

        boolean result = notificationService.markRead("u1", "n1");

        assertEquals(true, result);
    }

    @Test
    @DisplayName("边界场景：markRead 返回 0 时返回 false")
    void markRead返回0返回false() {
        when(msgNotificationMapper.markRead("n1", "u1")).thenReturn(0);

        boolean result = notificationService.markRead("u1", "n1");

        assertEquals(false, result);
    }

    @Test
    @DisplayName("边界场景：userId 为空时 markRead 返回 false")
    void markReadUserId为空返回false() {
        boolean result = notificationService.markRead(null, "n1");
        assertEquals(false, result);
    }

    @Test
    @DisplayName("边界场景：id 为空时 markRead 返回 false")
    void markReadId为空返回false() {
        boolean result = notificationService.markRead("u1", null);
        assertEquals(false, result);
    }

    @Test
    @DisplayName("正常场景：标记所有通知已读")
    void 标记所有已读() {
        when(msgNotificationMapper.markAllRead("u1")).thenReturn(5);

        int count = notificationService.markAllRead("u1");

        assertEquals(5, count);
    }

    @Test
    @DisplayName("边界场景：userId 为空时 markAllRead 返回 0")
    void markAllReadUserId为空返回0() {
        int count = notificationService.markAllRead(null);
        assertEquals(0, count);
    }

    // ==================== delete ====================

    @Test
    @DisplayName("正常场景：删除自己的通知")
    void 删除自己的通知() {
        MsgNotificationDO n = new MsgNotificationDO();
        n.setId("n1");
        n.setReceiverId("u1");
        when(msgNotificationMapper.selectById("n1")).thenReturn(n);

        notificationService.delete("u1", List.of("n1"));

        verify(msgNotificationMapper).deleteById("n1");
    }

    @Test
    @DisplayName("权限场景：删除他人通知时不执行删除")
    void 删除他人通知不执行() {
        MsgNotificationDO n = new MsgNotificationDO();
        n.setId("n1");
        n.setReceiverId("u2");
        when(msgNotificationMapper.selectById("n1")).thenReturn(n);

        notificationService.delete("u1", List.of("n1"));

        verify(msgNotificationMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("边界场景：userId 为空时不执行删除")
    void deleteUserId为空不执行() {
        notificationService.delete(null, List.of("n1"));
        verify(msgNotificationMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("边界场景：ids 为空时不执行删除")
    void ids为空不执行() {
        notificationService.delete("u1", null);
        verify(msgNotificationMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("边界场景：通知不存在时不执行删除")
    void 通知不存在不执行删除() {
        when(msgNotificationMapper.selectById("n1")).thenReturn(null);

        notificationService.delete("u1", List.of("n1"));

        verify(msgNotificationMapper, never()).deleteById(any());
    }

    // ==================== recall ====================

    @Test
    @DisplayName("正常场景：撤回委托 RecallService")
    void 撤回委托RecallService() {
        when(recallService.recallNotification("u1", "n1")).thenReturn(true);

        boolean result = notificationService.recall("u1", "n1");

        assertEquals(true, result);
        verify(recallService).recallNotification("u1", "n1");
    }
}
