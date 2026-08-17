package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgVariableSource;
import com.njydsz.message.infra.mapper.config.MsgVariableSourceMapper;
import com.njydsz.message.infra.repository.MsgVariableSourceRepository;

/**
 * 消息变量数据源 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgVariableSourceMapper} 实现 {@link MsgVariableSourceRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgVariableSourceRepositoryImpl implements MsgVariableSourceRepository {

  private final MsgVariableSourceMapper msgVariableSourceMapper;

  @Override
  public List<MsgVariableSource> selectList(LambdaQueryWrapper<MsgVariableSource> wrapper) {
    return msgVariableSourceMapper.selectList(wrapper);
  }
}
