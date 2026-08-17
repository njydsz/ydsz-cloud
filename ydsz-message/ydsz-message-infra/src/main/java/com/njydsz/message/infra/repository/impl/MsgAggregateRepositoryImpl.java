package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.batch.MsgAggregate;
import com.njydsz.message.infra.mapper.batch.MsgAggregateMapper;
import com.njydsz.message.infra.repository.MsgAggregateRepository;

/**
 * 聚合批次 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgAggregateMapper} 实现 {@link MsgAggregateRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgAggregateRepositoryImpl implements MsgAggregateRepository {

  private final MsgAggregateMapper msgAggregateMapper;

  @Override
  public int insert(MsgAggregate entity) {
    return msgAggregateMapper.insert(entity);
  }

  @Override
  public MsgAggregate selectById(String id) {
    return msgAggregateMapper.selectById(id);
  }

  @Override
  public int updateById(MsgAggregate entity) {
    return msgAggregateMapper.updateById(entity);
  }

  @Override
  public int update(LambdaUpdateWrapper<MsgAggregate> wrapper) {
    return msgAggregateMapper.update(null, wrapper);
  }

  @Override
  public List<MsgAggregate> selectList(LambdaQueryWrapper<MsgAggregate> wrapper) {
    return msgAggregateMapper.selectList(wrapper);
  }

  @Override
  public Page<MsgAggregate> selectPage(Page<MsgAggregate> page, LambdaQueryWrapper<MsgAggregate> wrapper) {
    return msgAggregateMapper.selectPage(page, wrapper);
  }
}
