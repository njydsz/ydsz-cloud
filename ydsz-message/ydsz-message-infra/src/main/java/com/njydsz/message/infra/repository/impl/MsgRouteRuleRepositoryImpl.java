package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.infra.mapper.config.MsgRouteRuleMapper;
import com.njydsz.message.infra.repository.MsgRouteRuleRepository;

/**
 * 消息路由规则 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgRouteRuleMapper} 实现 {@link MsgRouteRuleRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgRouteRuleRepositoryImpl implements MsgRouteRuleRepository {

  private final MsgRouteRuleMapper msgRouteRuleMapper;

  @Override
  public int insert(MsgRouteRule entity) {
    return msgRouteRuleMapper.insert(entity);
  }

  @Override
  public MsgRouteRule selectById(String id) {
    return msgRouteRuleMapper.selectById(id);
  }

  @Override
  public int updateById(MsgRouteRule entity) {
    return msgRouteRuleMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return msgRouteRuleMapper.deleteById(id);
  }

  @Override
  public List<MsgRouteRule> selectList(LambdaQueryWrapper<MsgRouteRule> wrapper) {
    return msgRouteRuleMapper.selectList(wrapper);
  }

  @Override
  public Page<MsgRouteRule> selectPage(Page<MsgRouteRule> page, LambdaQueryWrapper<MsgRouteRule> wrapper) {
    return msgRouteRuleMapper.selectPage(page, wrapper);
  }
}
