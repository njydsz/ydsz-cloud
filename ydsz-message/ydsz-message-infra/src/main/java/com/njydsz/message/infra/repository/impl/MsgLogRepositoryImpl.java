package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.infra.repository.MsgLogRepository;

/**
 * 消息发送日志 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgLogMapper} 实现 {@link MsgLogRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgLogRepositoryImpl implements MsgLogRepository {

  private final MsgLogMapper msgLogMapper;

  @Override
  public int insert(MsgLog entity) {
    return msgLogMapper.insert(entity);
  }

  @Override
  public MsgLog selectById(String id) {
    return msgLogMapper.selectById(id);
  }

  @Override
  public int updateById(MsgLog entity) {
    return msgLogMapper.updateById(entity);
  }

  @Override
  public int update(LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.update(null, wrapper);
  }

  @Override
  public List<MsgLog> selectList(LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.selectList(wrapper);
  }

  @Override
  public Long selectCount(LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.selectCount(wrapper);
  }

  @Override
  public Page<MsgLog> selectPage(Page<MsgLog> page, LambdaQueryWrapper<MsgLog> wrapper) {
    return msgLogMapper.selectPage(page, wrapper);
  }
}
