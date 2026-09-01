package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MsgNotificationDTO;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgNotification;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;

/**
 * 站内通知仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgNotificationRepository} 接口，封装 MsgNotificationMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgNotificationRepositoryImpl implements MsgNotificationRepository {

  private final MsgNotificationMapper msgNotificationMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgNotificationDTO dto) {
    if (dto == null) {
      return false;
    }
    MsgNotification entity = converter.dtoToEntity(dto);
    return msgNotificationMapper.insert(entity) > 0;
  }

  @Override
  public boolean saveBatch(List<MsgNotificationDTO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgNotification> entities = converter.notificationDtoListToEntity(list);
    return msgNotificationMapper.insertBatch(entities) > 0;
  }

  @Override
  public Optional<MsgNotificationVO> findById(String id) {
    return Optional.ofNullable(msgNotificationMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public boolean update(MsgNotificationDTO dto) {
    MsgNotification entity = converter.dtoToEntity(dto);
    return msgNotificationMapper.updateById(entity) > 0;
  }

  @Override
  public PageResponse<List<MsgNotificationVO>> findPage(NotificationQueryDTO query) {
    Page<MsgNotification> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgNotification> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgNotification> entityPage = msgNotificationMapper.selectPage(page, wrapper);
    List<MsgNotificationVO> vos = converter.notificationListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public List<MsgNotificationVO> findList(NotificationQueryDTO query) {
    QueryWrapper<MsgNotification> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    return converter.notificationListToVO(msgNotificationMapper.selectList(wrapper));
  }

  @Override
  public long count(NotificationQueryDTO query) {
    QueryWrapper<MsgNotification> wrapper = buildWrapper(query);
    Long count = msgNotificationMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean deleteById(String id) {
    return msgNotificationMapper.deleteById(id) > 0;
  }

  @Override
  public int markRead(String id, String userId) {
    return msgNotificationMapper.markRead(id, userId);
  }

  @Override
  public int markAllRead(String userId, int batchSize) {
    return msgNotificationMapper.markAllRead(userId, batchSize);
  }

  @Override
  public int markAllReadByBizType(String userId, String bizType) {
    QueryWrapper<MsgNotification> wrapper = new QueryWrapper<>();
    wrapper.eq("receiver_id", userId);
    wrapper.eq("read_status", 0);
    wrapper.eq("recall_status", "NONE");
    if (bizType != null && !bizType.isBlank()) {
      wrapper.eq("biz_type", bizType);
    }
    MsgNotification entity = new MsgNotification();
    entity.setReadStatus(1);
    entity.setReadTime(java.time.LocalDateTime.now());
    return msgNotificationMapper.update(entity, wrapper);
  }

  @Override
  public long countUnread(String userId) {
    Long count = msgNotificationMapper.countUnread(userId);
    return count != null ? count : 0L;
  }

  @Override
  public int markExpired(java.time.LocalDateTime now) {
    QueryWrapper<MsgNotification> wrapper = new QueryWrapper<>();
    wrapper.lt("expired_at", now);
    wrapper.eq("deleted", 0);
    MsgNotification entity = new MsgNotification();
    entity.setDeleted(true);
    return msgNotificationMapper.update(entity, wrapper);
  }

  private QueryWrapper<MsgNotification> buildWrapper(NotificationQueryDTO query) {
    QueryWrapper<MsgNotification> wrapper = new QueryWrapper<>();
    if (query.getReceiverId() != null && !query.getReceiverId().isBlank()) {
      wrapper.eq("receiver_id", query.getReceiverId());
    }
    if (query.getCategory() != null && !query.getCategory().isBlank()) {
      wrapper.eq("category", query.getCategory());
    }
    if (query.getLevel() != null && !query.getLevel().isBlank()) {
      wrapper.eq("level", query.getLevel());
    }
    if (query.getReadStatus() != null) {
      wrapper.eq("read_status", query.getReadStatus());
    }
    if (query.getIds() != null && !query.getIds().isEmpty()) {
      wrapper.in("id", query.getIds());
    }
    if (query.getTenantId() != null && !query.getTenantId().isBlank()) {
      wrapper.eq("tenant_id", query.getTenantId());
    }
    if (query.getMessageGroup() != null && !query.getMessageGroup().isBlank()) {
      wrapper.eq("message_group", query.getMessageGroup());
    }
    if (query.getRecallStatus() != null && !query.getRecallStatus().isBlank()) {
      wrapper.eq("recall_status", query.getRecallStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

}
