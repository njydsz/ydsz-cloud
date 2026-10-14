package com.njydsz.message.server.service.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.domain.enums.receipt.ReadStatusEnum;
import com.njydsz.message.domain.identity.IdGenerator;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.domain.vo.NotificationGroupVO;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.impl.NotificationServiceImpl;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * NotificationServiceImpl 单元测试
 *
 * <p>验证站内通知发送、收件箱查询、已读标记、全部已读分批、分组折叠、撤回等核心能力。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private MsgNotificationRepository msgNotificationRepository;
  @Mock private RealtimePushService realtimePushService;
  @Mock private RecallService recallService;
  @Mock private MessageProperties messageProperties;
  @Mock private IdGenerator idGenerator;

  @InjectMocks
  private NotificationServiceImpl notificationService;

  // ========================= Fixtures =========================

  private NotificationSendDTO buildSendDto(List<String> receiverIds) {
    NotificationSendDTO dto = new NotificationSendDTO();
    dto.setTitle("System Alert");
    dto.setContent("Your order has been processed");
    dto.setReceiverIds(receiverIds);
    dto.setBizType("ORDER");
    dto.setLevel("INFO");
    dto.setCategory("SYSTEM");
    return dto;
  }

  private MsgNotificationVO buildNotification(String id, String receiverId, String group,
      ReadStatusEnum readStatus) {
    MsgNotificationVO vo = new MsgNotificationVO();
    vo.setId(id);
    vo.setTitle("Alert");
    vo.setContent("Content-" + id);
    vo.setReceiverId(receiverId);
    vo.setMessageGroup(group);
    vo.setReadStatus(readStatus);
    vo.setLevel("INFO");
    vo.setCategory("SYSTEM");
    vo.setCreatedAt(LocalDateTime.now());
    return vo;
  }

  // ========================= Send =========================

  @Test
  @DisplayName("send - 群发：为每个接收人写入通知并返回总条数")
  void should_saveAndReturnCount_when_sendingToMultipleReceivers() {
    // Arrange
    List<String> receivers = List.of("user-1", "user-2", "user-3");
    NotificationSendDTO dto = buildSendDto(receivers);

    when(idGenerator.nextId()).thenReturn("id-1", "id-2", "id-3");
    when(msgNotificationRepository.saveBatch(any())).thenReturn(true);

    // Act
    int count = notificationService.send(dto);

    // Assert
    assertThat(count).isEqualTo(3);
    verify(msgNotificationRepository).saveBatch(any());
    verify(realtimePushService, times(3)).pushToUserWithOffline(anyString(), anyString(), any());
  }

  @Test
  @DisplayName("send - 单发 fallback：receiverIds 为空但 receiverId 存在时用单发模式")
  void should_fallbackToSingleReceiver_when_receiverIdsEmpty() {
    // Arrange
    NotificationSendDTO dto = new NotificationSendDTO();
    dto.setTitle("Single");
    dto.setContent("Single content");
    dto.setReceiverId("only-one");

    when(idGenerator.nextId()).thenReturn("id-single");
    when(msgNotificationRepository.saveBatch(any())).thenReturn(true);

    // Act
    int count = notificationService.send(dto);

    // Assert
    assertThat(count).isEqualTo(1);
    verify(realtimePushService).pushToUserWithOffline(eq("only-one"), anyString(), any());
  }

  @Test
  @DisplayName("send - dto 为 null 抛出异常拒绝发送")
  void should_throwException_when_dtoIsNull() {
    // Act & Assert
    assertThatThrownBy(() -> notificationService.send(null))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("send - 接收人列表为 null 且无 receiverId 抛出异常拒绝发送")
  void should_throwException_when_noReceiver() {
    // Arrange
    NotificationSendDTO dto = new NotificationSendDTO();
    dto.setTitle("No Receiver");

    // Act & Assert
    assertThatThrownBy(() -> notificationService.send(dto))
        .isInstanceOf(RuntimeException.class);
  }

  // ========================= Inbox =========================

  @Test
  @DisplayName("inbox - 正常查询：设置接收人 ID 并分页返回")
  void should_returnPage_when_queryingInbox() {
    // Arrange
    NotificationQueryDTO query = new NotificationQueryDTO();
    query.setPageNum(1);
    query.setPageSize(10);

    MsgNotificationVO vo = buildNotification("n-1", "user-1", "order-001", ReadStatusEnum.UNREAD);
    PageResponse<List<MsgNotificationVO>> pageResult = PageResponse.success(1L, 1L, 10L, List.of(vo));
    when(msgNotificationRepository.findPage(any(NotificationQueryDTO.class))).thenReturn(pageResult);

    // Act
    PageResponse<List<MsgNotificationVO>> result = notificationService.inbox("user-1", query);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getId()).isEqualTo("n-1");

    ArgumentCaptor<NotificationQueryDTO> captor = ArgumentCaptor.forClass(NotificationQueryDTO.class);
    verify(msgNotificationRepository).findPage(captor.capture());
    assertThat(captor.getValue().getReceiverId()).isEqualTo("user-1");
    assertThat(captor.getValue().getPageNum()).isEqualTo(1);
  }

  @Test
  @DisplayName("inbox - query 为 null：使用默认查询参数，pageSize 不超过上限")
  void should_useDefaultQuery_when_queryIsNull() {
    // Arrange
    when(msgNotificationRepository.findPage(any(NotificationQueryDTO.class)))
        .thenReturn(PageResponse.success(0L, 1L, 10L, List.of()));

    // Act
    notificationService.inbox("user-1", null);

    // Assert
    ArgumentCaptor<NotificationQueryDTO> captor = ArgumentCaptor.forClass(NotificationQueryDTO.class);
    verify(msgNotificationRepository).findPage(captor.capture());
    assertThat(captor.getValue().getPageSize()).isLessThanOrEqualTo(PageConstants.MAX_PAGE_SIZE);
  }

  @Test
  @DisplayName("inbox - userId 为空：抛出异常拒绝查询")
  void should_throwException_when_userIdIsBlank() {
    assertThatThrownBy(() -> notificationService.inbox("", new NotificationQueryDTO()))
        .isInstanceOf(RuntimeException.class);

    assertThatThrownBy(() -> notificationService.inbox(null, new NotificationQueryDTO()))
        .isInstanceOf(RuntimeException.class);
  }

  // ========================= Count =========================

  @Test
  @DisplayName("countUnread - 正常统计：返回未读条数")
  void should_returnUnreadCount_when_userHasUnread() {
    // Arrange
    when(msgNotificationRepository.countUnread("user-1")).thenReturn(5L);

    // Act
    long count = notificationService.countUnread("user-1");

    // Assert
    assertThat(count).isEqualTo(5L);
  }

  @Test
  @DisplayName("countUnread - repository 返回 0：正确返回 0")
  void should_returnZero_when_repositoryReturnsZero() {
    // Arrange
    when(msgNotificationRepository.countUnread("user-1")).thenReturn(0L);

    // Act
    long count = notificationService.countUnread("user-1");

    // Assert
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("countUnread - userId 为空：直接返回 0 不查库")
  void should_returnZeroWithoutQuery_when_userIdBlank() {
    // Act
    long count = notificationService.countUnread("");

    // Assert
    assertThat(count).isZero();
    verify(msgNotificationRepository, never()).countUnread(anyString());
  }

  // ========================= Mark Read =========================

  @Test
  @DisplayName("markRead - 成功标记：userId 和 id 有效返回 true")
  void should_returnTrue_when_markReadSucceeds() {
    // Arrange
    when(msgNotificationRepository.markRead("notif-1", "user-1")).thenReturn(1);

    // Act
    boolean result = notificationService.markRead("user-1", "notif-1");

    // Assert
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("markRead - userId 为空：返回 false 不执行")
  void should_returnFalse_when_userIdBlank() {
    // Act
    boolean result = notificationService.markRead("", "notif-1");

    // Assert
    assertThat(result).isFalse();
    verify(msgNotificationRepository, never()).markRead(anyString(), anyString());
  }

  @Test
  @DisplayName("markRead - 通知不存在或越权：返回 false")
  void should_returnFalse_when_notificationNotBelongToUser() {
    // Arrange
    when(msgNotificationRepository.markRead("notif-1", "user-2")).thenReturn(0);

    // Act
    boolean result = notificationService.markRead("user-2", "notif-1");

    // Assert
    assertThat(result).isFalse();
  }

  // ========================= Mark All Read =========================

  @Test
  @DisplayName("markAllRead - 分批模式：多次调用 markAllRead 直到处理完毕")
  void should_loopUntilAllProcessed_when_batchMode() {
    // Arrange
    when(messageProperties.getMarkAllReadBatchSize()).thenReturn(2);
    when(msgNotificationRepository.markAllRead("user-1", 2))
        .thenReturn(2)
        .thenReturn(2)
        .thenReturn(1);

    // Act
    int total = notificationService.markAllRead("user-1");

    // Assert
    assertThat(total).isEqualTo(5); // 2 + 2 + 1
    verify(msgNotificationRepository, times(3)).markAllRead("user-1", 2);
  }

  @Test
  @DisplayName("markAllRead - 单次模式：batchSize <= 0 时执行一次全量更新")
  void should_singleBatch_when_batchSizeZero() {
    // Arrange
    when(messageProperties.getMarkAllReadBatchSize()).thenReturn(0);
    when(msgNotificationRepository.markAllRead("user-1", 0)).thenReturn(10);

    // Act
    int total = notificationService.markAllRead("user-1");

    // Assert
    assertThat(total).isEqualTo(10);
    verify(msgNotificationRepository, times(1)).markAllRead("user-1", 0);
  }

  @Test
  @DisplayName("markAllRead - userId 为空：直接返回 0 不执行")
  void should_returnZero_when_userIdBlank() {
    // Act
    int total = notificationService.markAllRead("");

    // Assert
    assertThat(total).isZero();
    verify(msgNotificationRepository, never()).markAllRead(anyString(), anyInt());
  }

  // ========================= Delete =========================

  @Test
  @DisplayName("delete - 正常删除：查询后逐条删除")
  void should_deleteByIds_when_validRequest() {
    // Arrange
    MsgNotificationVO vo1 = buildNotification("n-1", "user-1", "group-1", ReadStatusEnum.UNREAD);
    MsgNotificationVO vo2 = buildNotification("n-2", "user-1", "group-1", ReadStatusEnum.READ);
    when(msgNotificationRepository.findList(any(NotificationQueryDTO.class)))
        .thenReturn(List.of(vo1, vo2));

    // Act
    notificationService.delete("user-1", List.of("n-1", "n-2"));

    // Assert
    verify(msgNotificationRepository).deleteById("n-1");
    verify(msgNotificationRepository).deleteById("n-2");
  }

  @Test
  @DisplayName("delete - ids 为空列表：静默返回不执行任何操作")
  void should_returnWithoutAction_when_idsEmpty() {
    // Act
    notificationService.delete("user-1", List.of());

    // Assert
    verify(msgNotificationRepository, never()).deleteById(anyString());
  }

  // ========================= Recall =========================

  @Test
  @DisplayName("recall - 委托 recallService：返回撤回结果")
  void should_delegateToRecallService() {
    // Arrange
    when(recallService.recallNotification("user-1", "notif-1")).thenReturn(true);

    // Act
    boolean result = notificationService.recall("user-1", "notif-1");

    // Assert
    assertThat(result).isTrue();
    verify(recallService).recallNotification("user-1", "notif-1");
  }

  @Test
  @DisplayName("recall - recallService 返回 false：撤回失败")
  void should_returnFalse_when_recallFails() {
    // Arrange
    when(recallService.recallNotification("user-1", "notif-1")).thenReturn(false);

    // Act
    boolean result = notificationService.recall("user-1", "notif-1");

    // Assert
    assertThat(result).isFalse();
  }

  // ========================= Inbox Grouped =========================

  @Test
  @DisplayName("inboxGrouped - 按 message_group 分组：计算每组未读/总数")
  void should_groupByMessageGroup_withCorrectCounts() {
    // Arrange
    MsgNotificationVO n1 = buildNotification("n-1", "user-1", "order-001", ReadStatusEnum.UNREAD);
    MsgNotificationVO n2 = buildNotification("n-2", "user-1", "order-001", ReadStatusEnum.READ);
    MsgNotificationVO n3 = buildNotification("n-3", "user-1", "order-002", ReadStatusEnum.UNREAD);

    when(msgNotificationRepository.findPage(any(NotificationQueryDTO.class)))
        .thenReturn(PageResponse.success(3L, 1L, 10L, List.of(n1, n2, n3)));

    // Act
    PageResponse<List<NotificationGroupVO>> result =
        notificationService.inboxGrouped("user-1", new NotificationQueryDTO());

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getData()).hasSize(2);

    NotificationGroupVO group1 = result.getData().stream()
        .filter(g -> "order-001".equals(g.getMessageGroup())).findFirst().orElseThrow();
    assertThat(group1.getTotalCount()).isEqualTo(2);
    assertThat(group1.getUnreadCount()).isEqualTo(1);

    NotificationGroupVO group2 = result.getData().stream()
        .filter(g -> "order-002".equals(g.getMessageGroup())).findFirst().orElseThrow();
    assertThat(group2.getTotalCount()).isEqualTo(1);
    assertThat(group2.getUnreadCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("inboxGrouped - 无分组键的通知：使用 UNG:id 独立成组")
  void should_createUngroupedKey_when_messageGroupIsNull() {
    // Arrange
    MsgNotificationVO n1 = buildNotification("n-1", "user-1", null, ReadStatusEnum.UNREAD);
    when(msgNotificationRepository.findPage(any(NotificationQueryDTO.class)))
        .thenReturn(PageResponse.success(1L, 1L, 10L, List.of(n1)));

    // Act
    PageResponse<List<NotificationGroupVO>> result =
        notificationService.inboxGrouped("user-1", new NotificationQueryDTO());

    // Assert
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getMessageGroup()).startsWith("UNG:");
  }

  // ========================= List By Group =========================

  @Test
  @DisplayName("listByGroup - 正常查询：返回该分组下的全部通知")
  void should_returnGroupNotifications_when_validParams() {
    // Arrange
    MsgNotificationVO n1 = buildNotification("n-1", "user-1", "order-001", ReadStatusEnum.UNREAD);
    MsgNotificationVO n2 = buildNotification("n-2", "user-1", "order-001", ReadStatusEnum.READ);
    when(msgNotificationRepository.findList(any(NotificationQueryDTO.class)))
        .thenReturn(List.of(n1, n2));

    // Act
    List<MsgNotificationVO> result = notificationService.listByGroup("user-1", "order-001");

    // Assert
    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("listByGroup - userId 或 messageGroup 为空：返回空列表")
  void should_returnEmptyList_when_paramsBlank() {
    // Act
    List<MsgNotificationVO> result1 = notificationService.listByGroup("", "group");
    List<MsgNotificationVO> result2 = notificationService.listByGroup("user-1", "");

    // Assert
    assertThat(result1).isEmpty();
    assertThat(result2).isEmpty();
  }
}
