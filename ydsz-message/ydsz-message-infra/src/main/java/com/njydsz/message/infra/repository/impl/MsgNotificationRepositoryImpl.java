package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;
import com.njydsz.message.infra.repository.MsgNotificationRepository;

/**
 * 站内通知 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgNotificationMapper} 实现 {@link MsgNotificationRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgNotificationRepositoryImpl implements MsgNotificationRepository {

  private final MsgNotificationMapper msgNotificationMapper;

  @Override
  public int insertBatch(List<MsgNotification> list) {
    return msgNotificationMapper.insertBatch(list);
  }

  @Override
  public MsgNotification selectById(String id) {
    return msgNotificationMapper.selectById(id);
  }

  @Override
  public int updateById(MsgNotification entity) {
    return msgNotificationMapper.updateById(entity);
  }

  @Override
  public int update(LambdaUpdateWrapper<MsgNotification> wrapper) {
    return msgNotificationMapper.update(null, wrapper);
  }

  @Override
  public List<MsgNotification> selectList(LambdaQueryWrapper<MsgNotification> wrapper) {
    return msgNotificationMapper.selectList(wrapper);
  }

  @Override
  public Long selectCount(LambdaQueryWrapper<MsgNotification> wrapper) {
    return msgNotificationMapper.selectCount(wrapper);
  }

  @Override
  public Page<MsgNotification> selectPage(Page<MsgNotification> page, LambdaQueryWrapper<MsgNotification> wrapper) {
    return msgNotificationMapper.selectPage(page, wrapper);
  }

  @Override
  public int deleteById(String id) {
    return msgNotificationMapper.deleteById(id);
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
  public Long countUnread(String userId) {
    return msgNotificationMapper.countUnread(userId);
  }
}
