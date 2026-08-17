package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.IPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgOffline;
import com.njydsz.message.infra.mapper.config.MsgOfflineMapper;
import com.njydsz.message.infra.repository.MsgOfflineRepository;

/**
 * 离线消息 Repository 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgOfflineRepositoryImpl implements MsgOfflineRepository {

  private final MsgOfflineMapper msgOfflineMapper;

  @Override
  public int insertBatch(List<MsgOffline> list) {
    return msgOfflineMapper.insertBatch(list);
  }

  @Override
  public int markPushedByUser(String userId) {
    return msgOfflineMapper.markPushedByUser(userId);
  }

  @Override
  public int markExpired() {
    return msgOfflineMapper.markExpired();
  }

  @Override
  public int insert(MsgOffline entity) {
    return msgOfflineMapper.insert(entity);
  }

  @Override
  public List<MsgOffline> selectList(LambdaQueryWrapper<MsgOffline> wrapper) {
    return msgOfflineMapper.selectList(wrapper);
  }

  @Override
  public Long selectCount(LambdaQueryWrapper<MsgOffline> wrapper) {
    return msgOfflineMapper.selectCount(wrapper);
  }

  @Override
  public IPage<MsgOffline> selectPage(IPage<MsgOffline> page, LambdaQueryWrapper<MsgOffline> wrapper) {
    return msgOfflineMapper.selectPage(page, wrapper);
  }
}
