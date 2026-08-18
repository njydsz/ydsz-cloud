package com.njydsz.message.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.dto.core.NotificationQueryDTO;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgNotificationDO;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;

/**
 * 站内通知仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgNotificationRepository} 接口，封装 MsgNotificationMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgNotificationRepositoryImpl implements MsgNotificationRepository {

  private final MsgNotificationMapper msgNotificationMapper;

  private final MessageConverter converter;

  @Override
  public boolean saveBatch(List<MsgNotificationVO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgNotificationDO> entities = list.stream().map(this::voToDO).toList();
    return msgNotificationMapper.insertBatch(entities) > 0;
  }

  @Override
  public Optional<MsgNotificationVO> findById(String id) {
    return Optional.ofNullable(msgNotificationMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgNotificationVO vo) {
    MsgNotificationDO entity = voToDO(vo);
    return msgNotificationMapper.updateById(entity) > 0;
  }

  @Override
  public IPage<MsgNotificationVO> findPage(NotificationQueryDTO query) {
    Page<MsgNotificationDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgNotificationDO> entityPage = msgNotificationMapper.selectPage(page, wrapper);
    List<MsgNotificationVO> vos = converter.notificationDoListToVO(entityPage.getRecords());
    Page<MsgNotificationVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
    voPage.setRecords(vos);
    return voPage;
  }

  @Override
  public List<MsgNotificationVO> findList(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    return converter.notificationDoListToVO(msgNotificationMapper.selectList(wrapper));
  }

  @Override
  public long count(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
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
  public long countUnread(String userId) {
    Long count = msgNotificationMapper.countUnread(userId);
    return count != null ? count : 0L;
  }

  private QueryWrapper<MsgNotificationDO> buildWrapper(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = new QueryWrapper<>();
    if (query.getCategory() != null && !query.getCategory().isBlank()) {
      wrapper.eq("category", query.getCategory());
    }
    if (query.getLevel() != null && !query.getLevel().isBlank()) {
      wrapper.eq("level", query.getLevel());
    }
    if (query.getReadStatus() != null) {
      wrapper.eq("read_status", query.getReadStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgNotificationDO voToDO(MsgNotificationVO vo) {
    if (vo == null) {
      return null;
    }
    MsgNotificationDO entity = new MsgNotificationDO();
    entity.setId(vo.getId());
    entity.setTitle(vo.getTitle());
    entity.setContent(vo.getContent());
    entity.setLevel(vo.getLevel());
    entity.setCategory(vo.getCategory());
    entity.setPriority(vo.getPriority());
    entity.setSenderId(vo.getSenderId());
    entity.setReceiverId(vo.getReceiverId());
    entity.setBizType(vo.getBizType());
    entity.setBizId(vo.getBizId());
    entity.setMessageGroup(vo.getMessageGroup());
    entity.setBatchId(vo.getBatchId());
    entity.setActionUrl(vo.getActionUrl());
    entity.setActionText(vo.getActionText());
    entity.setIcon(vo.getIcon());
    entity.setExtra(vo.getExtra());
    entity.setSourceModule(vo.getSourceModule());
    entity.setReadStatus(vo.getReadStatus());
    entity.setReadTime(vo.getReadTime());
    entity.setRecallStatus(vo.getRecallStatus());
    entity.setRecallAt(vo.getRecallAt());
    entity.setExpiredAt(vo.getExpiredAt());
    entity.setMentionUserIds(vo.getMentionUserIds());
    return entity;
  }
}
