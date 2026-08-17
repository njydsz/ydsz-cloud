package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.batch.MsgBatch;
import com.njydsz.message.infra.mapper.batch.MsgBatchMapper;
import com.njydsz.message.infra.repository.MsgBatchRepository;

/**
 * 消息批次 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgBatchMapper} 实现 {@link MsgBatchRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgBatchRepositoryImpl implements MsgBatchRepository {

  private final MsgBatchMapper msgBatchMapper;

  @Override
  public int insert(MsgBatch entity) {
    return msgBatchMapper.insert(entity);
  }

  @Override
  public MsgBatch selectById(String id) {
    return msgBatchMapper.selectById(id);
  }

  @Override
  public int updateById(MsgBatch entity) {
    return msgBatchMapper.updateById(entity);
  }

  @Override
  public List<MsgBatch> selectList(LambdaQueryWrapper<MsgBatch> wrapper) {
    return msgBatchMapper.selectList(wrapper);
  }
}
