package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgSubscription;
import com.njydsz.message.infra.mapper.config.MsgSubscriptionMapper;
import com.njydsz.message.infra.repository.MsgSubscriptionRepository;

/**
 * 消息订阅关系 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgSubscriptionMapper} 实现 {@link MsgSubscriptionRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgSubscriptionRepositoryImpl implements MsgSubscriptionRepository {

  private final MsgSubscriptionMapper msgSubscriptionMapper;

  @Override
  public int insert(MsgSubscription entity) {
    return msgSubscriptionMapper.insert(entity);
  }

  @Override
  public int updateById(MsgSubscription entity) {
    return msgSubscriptionMapper.updateById(entity);
  }

  @Override
  public MsgSubscription selectOne(LambdaQueryWrapper<MsgSubscription> wrapper) {
    return msgSubscriptionMapper.selectOne(wrapper);
  }

  @Override
  public List<MsgSubscription> selectList(LambdaQueryWrapper<MsgSubscription> wrapper) {
    return msgSubscriptionMapper.selectList(wrapper);
  }

  @Override
  public Long selectCount(LambdaQueryWrapper<MsgSubscription> wrapper) {
    return msgSubscriptionMapper.selectCount(wrapper);
  }

  @Override
  public Page<MsgSubscription> selectPage(Page<MsgSubscription> page, LambdaQueryWrapper<MsgSubscription> wrapper) {
    return msgSubscriptionMapper.selectPage(page, wrapper);
  }
}
