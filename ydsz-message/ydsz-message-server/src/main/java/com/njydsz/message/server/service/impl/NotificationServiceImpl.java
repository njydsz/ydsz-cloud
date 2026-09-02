package com.njydsz.message.server.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.MsgNotificationDTO;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.dto.NotificationSendDTO;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.domain.vo.NotificationGroupVO;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.NotificationService;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * 通知服务实现。
 *
 * <p>面向业务系统的统一通知 API：支持即时通知、定时通知、聚合通知、撤回、状态查询。
 *
 * <p>本服务是 {@code ydsz-message} 对外的「门面」，内部委托给具体业务服务（Template/Batch/Receipt 等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
  /** 最大轮询次数（安全护栏） */
  private static final int MAX_ROUNDS = 200;


  /** P3-6: 批量 insert 单批最大条数（ydsz_msg_notification 28 列，500 条 ≈ 1.4 万参数，远低于 PG 65535 上限） */
  private static final int INSERT_BATCH_SIZE = 500;

  /** 站内通知 Repository */
  private final MsgNotificationRepository msgNotificationRepository;

  /** 实时推送服务（WebSocket / 离线缓存） */
  private final RealtimePushService realtimePushService;

  /** P2-18: 站内通知全文搜索索引 */
  private final NotificationSearchService notificationSearchService;

  /** 消息撤回服务 */
  private final RecallService recallService;

  /** P2-6: 全局配置（读取 markAllReadBatchSize） */
  private final MessageProperties messageProperties;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int send(NotificationSendDTO dto) {
    if (dto == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("通知参数不能为空")
          .build();
    }
    List<String> receiverIds = resolveReceiverIds(dto);
    // P3-6: 先构建全部实体（预生成 ID），再批量 insert，避免逐条 INSERT 的数据库往返开销
    List<MsgNotificationVO> entities = new ArrayList<>(receiverIds.size());
    for (String rid : receiverIds) {
      MsgNotificationVO entity = buildEntity(dto, rid);
      entity.setId(IdWorker.getIdStr());
      entities.add(entity);
    }
    // 分批批量 insert（防止单条 SQL 参数超过 PG 65535 上限）
    for (int i = 0; i < entities.size(); i += INSERT_BATCH_SIZE) {
      int to = Math.min(i + INSERT_BATCH_SIZE, entities.size());
      List<MsgNotificationDTO> batch = new ArrayList<>(to - i);
      for (MsgNotificationVO vo : entities.subList(i, to)) {
        batch.add(voToDto(vo));
      }
      msgNotificationRepository.saveBatch(batch);
    }
    // 批量 insert 完成后再循环做 index + push（entity.id 已有值）
    for (int i = 0; i < entities.size(); i++) {
      MsgNotificationVO entity = entities.get(i);
      String rid = receiverIds.get(i);
      // P2-18: 构建全文搜索索引
      notificationSearchService.index(rid, entity.getId(), dto.getTitle(), entity.getContent());
      // 实时推送（P0-4: 离线时自动缓存到 Redis，上线时补偿）
      realtimePushService.pushToUserWithOffline(rid, "NOTIFICATION", entity);
    }
    int count = entities.size();
    log.info(
        "[Notification] 发送通知: title={} count={} bizType={}",
        dto.getTitle(),
        count,
        dto.getBizType());
    return count;
  }

  @Override
  public PageResponse<List<MsgNotificationVO>> inbox(String userId, NotificationQueryDTO query) {
    if (!StringUtils.hasText(userId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID 不能为空")
          .build();
    }
    NotificationQueryDTO effectiveQuery = query != null ? query : new NotificationQueryDTO();
    effectiveQuery.setReceiverId(userId);
    effectiveQuery.setPageNum(effectiveQuery.getPageNum() != null ? effectiveQuery.getPageNum() : 1);
    effectiveQuery.setPageSize(
        Math.min(
            effectiveQuery.getPageSize() != null ? effectiveQuery.getPageSize() : 10,
            PageConstants.MAX_PAGE_SIZE));
    return msgNotificationRepository.findPage(effectiveQuery);
  }

  @Override
  public long countUnread(String userId) {
    if (!StringUtils.hasText(userId)) {
      return 0L;
    }
    Long count = msgNotificationRepository.countUnread(userId);
    return count == null ? 0L : count;
  }

  @Override
  public boolean markRead(String userId, String id) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(id)) {
      return false;
    }
    return msgNotificationRepository.markRead(id, userId) > 0;
  }

  @Override
  public int markAllRead(String userId) {
    if (!StringUtils.hasText(userId)) {
      return 0;
    }
    // P2-6: 分批 UPDATE 避免长事务，每批独立事务（无外层 @Transactional）
    int batchSize = messageProperties.getMarkAllReadBatchSize();
    if (batchSize <= 0) {
      // 配置为非正数时退化为单次全量 UPDATE（兼容兜底）
      return msgNotificationRepository.markAllRead(userId, 0);
    }
    int total = 0;
    int rounds = 0;
    int maxRounds = MAX_ROUNDS; // 安全护栏：防止极端情况下死循环（200 * 500 = 10 万条）
    while (rounds++ < maxRounds) {
      int affected = msgNotificationRepository.markAllRead(userId, batchSize);
      total += affected;
      if (affected < batchSize) {
        break;
      }
    }
    if (total > batchSize) {
      log.info(
          "[Notification] markAllRead 分批完成: userId={} total={} rounds={}",
          userId,
          total,
          rounds - 1);
    }
    return total;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void delete(String userId, List<String> ids) {
    if (!StringUtils.hasText(userId) || CollectionUtils.isEmpty(ids)) {
      return;
    }
    // P2-7: 批量查询替代逐条 selectById，减少 N 次 DB 往返
    NotificationQueryDTO query = new NotificationQueryDTO();
    query.setIds(ids);
    query.setReceiverId(userId);
    List<MsgNotificationVO> notifications = msgNotificationRepository.findList(query);
    for (MsgNotificationVO n : notifications) {
      // P2-18: 移除全文搜索索引
      notificationSearchService.removeIndex(userId, n.getId(), n.getTitle(), n.getContent());
      msgNotificationRepository.deleteById(n.getId());
    }
  }

  @Override
  public boolean recall(String userId, String id) {
    return recallService.recallNotification(userId, id);
  }

  @Override
  public PageResponse<List<NotificationGroupVO>> inboxGrouped(String userId, NotificationQueryDTO query) {
    // 查询用户全部通知（按时间倒序），按 message_group 折叠
    PageResponse<List<MsgNotificationVO>> allPage = inbox(userId, query);
    Map<String, NotificationGroupVO> groupMap = new LinkedHashMap<>(16);

    for (MsgNotificationVO n : allPage.getData()) {
      String groupKey = n.getMessageGroup();
      if (!StringUtils.hasText(groupKey)) {
        // 无分组键的消息独立成组（用 id 作为 groupKey）
        groupKey = "UNG:" + n.getId();
      }
      NotificationGroupVO vo = groupMap.get(groupKey);
      if (vo == null) {
        vo = new NotificationGroupVO();
        vo.setMessageGroup(groupKey);
        vo.setLatestId(n.getId());
        vo.setLatestTitle(n.getTitle());
        vo.setLatestContent(n.getContent());
        vo.setLatestTime(n.getCreatedAt());
        vo.setLatestLevel(n.getLevel());
        vo.setLatestCategory(n.getCategory());
        vo.setUnreadCount(0);
        vo.setTotalCount(0);
        groupMap.put(groupKey, vo);
      }
      vo.setTotalCount(vo.getTotalCount() + 1);
      if (n.getReadStatus() != null && n.getReadStatus() == 0) {
        vo.setUnreadCount(vo.getUnreadCount() + 1);
      }
    }

    return PageResponse.success(
        allPage.getTotal(),
        allPage.getPageNum(),
        allPage.getPageSize(),
        new ArrayList<>(groupMap.values()));
  }

  @Override
  public List<MsgNotificationVO> listByGroup(String userId, String messageGroup) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(messageGroup)) {
      return List.of();
    }
    NotificationQueryDTO query = new NotificationQueryDTO();
    query.setReceiverId(userId);
    query.setMessageGroup(messageGroup);
    query.setTenantId(TenantContextHolder.getTenantId());
    return msgNotificationRepository.findList(query);
  }

  private List<String> resolveReceiverIds(NotificationSendDTO dto) {
    List<String> receiverIds = dto.getReceiverIds();
    if (CollectionUtils.isEmpty(receiverIds) && dto.getReceiverId() != null) {
      receiverIds = List.of(dto.getReceiverId());
    }
    if (CollectionUtils.isEmpty(receiverIds)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("接收人不能为空")
          .build();
    }
    return receiverIds;
  }

  /**
   * 将通知 VO 转换为 DTO（用于 Repository 层 CUD 操作）。
   *
   * @param vo 通知 VO
   * @return 通知 DTO
   */
  private MsgNotificationDTO voToDto(MsgNotificationVO vo) {
    MsgNotificationDTO dto = new MsgNotificationDTO();
    dto.setId(vo.getId());
    dto.setTitle(vo.getTitle());
    dto.setContent(vo.getContent());
    dto.setLevel(vo.getLevel());
    dto.setCategory(vo.getCategory());
    dto.setPriority(vo.getPriority());
    dto.setSenderId(vo.getSenderId());
    dto.setReceiverId(vo.getReceiverId());
    dto.setBizType(vo.getBizType());
    dto.setBizId(vo.getBizId());
    dto.setMessageGroup(vo.getMessageGroup());
    dto.setBatchId(vo.getBatchId());
    dto.setActionUrl(vo.getActionUrl());
    dto.setActionText(vo.getActionText());
    dto.setIcon(vo.getIcon());
    dto.setExtra(vo.getExtra());
    dto.setSourceModule(vo.getSourceModule());
    dto.setReadStatus(vo.getReadStatus());
    dto.setReadTime(vo.getReadTime());
    dto.setRecallStatus(vo.getRecallStatus());
    dto.setRecallAt(vo.getRecallAt());
    dto.setExpiredAt(vo.getExpiredAt());
    dto.setMentionUserIds(vo.getMentionUserIds());
    dto.setStatus(vo.getStatus());
    dto.setCreatedBy(vo.getCreatedBy());
    dto.setCreatedAt(vo.getCreatedAt());
    dto.setUpdatedBy(vo.getUpdatedBy());
    dto.setUpdatedAt(vo.getUpdatedAt());
    return dto;
  }

  private MsgNotificationVO buildEntity(NotificationSendDTO dto, String receiverId) {
    MsgNotificationVO n = new MsgNotificationVO();
    n.setTitle(dto.getTitle());
    n.setContent(dto.getContent());
    n.setLevel(StringUtils.hasText(dto.getLevel()) ? dto.getLevel() : "INFO");
    n.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : "SYSTEM");
    n.setPriority(dto.getPriority());
    n.setSenderId(
        StringUtils.hasText(dto.getSenderId())
            ? dto.getSenderId()
            : SystemConstants.SYSTEM_USER_ID);
    n.setReceiverId(receiverId);
    n.setBizType(dto.getBizType());
    n.setBizId(dto.getBizId());
    n.setMessageGroup(dto.getMessageGroup());
    n.setActionUrl(dto.getActionUrl());
    n.setActionText(dto.getActionText());
    n.setIcon(dto.getIcon());
    n.setExtra(dto.getExtra());
    n.setSourceModule(dto.getSourceModule());
    n.setReadStatus(0);
    n.setRecallStatus(RecallStatusEnum.NONE.name());
    n.setExpiredAt(dto.getExpiredAt());
    // P2-7: 补齐租户隔离,与其他消息实体一致(原依赖 DB DEFAULT '1',多租户场景会越权)
    n.setTenantId(TenantContextHolder.getTenantId());
    return n;
  }
}
