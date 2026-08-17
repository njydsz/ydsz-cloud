package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgTrace;
import com.njydsz.message.infra.mapper.config.MsgTraceMapper;
import com.njydsz.message.infra.repository.MsgTraceRepository;

/**
 * 消息轨迹 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgTraceMapper} 实现 {@link MsgTraceRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTraceRepositoryImpl implements MsgTraceRepository {

  private final MsgTraceMapper msgTraceMapper;

  @Override
  public int insert(MsgTrace entity) {
    return msgTraceMapper.insert(entity);
  }

  @Override
  public List<MsgTrace> selectList(LambdaQueryWrapper<MsgTrace> wrapper) {
    return msgTraceMapper.selectList(wrapper);
  }
}
