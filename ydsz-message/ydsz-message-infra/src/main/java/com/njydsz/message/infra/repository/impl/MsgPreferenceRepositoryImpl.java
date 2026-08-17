package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.infra.mapper.config.MsgPreferenceMapper;
import com.njydsz.message.infra.repository.MsgPreferenceRepository;

/**
 * 用户消息偏好 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgPreferenceMapper} 实现 {@link MsgPreferenceRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgPreferenceRepositoryImpl implements MsgPreferenceRepository {

  private final MsgPreferenceMapper msgPreferenceMapper;

  @Override
  public int insert(MsgPreference entity) {
    return msgPreferenceMapper.insert(entity);
  }

  @Override
  public int updateById(MsgPreference entity) {
    return msgPreferenceMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return msgPreferenceMapper.deleteById(id);
  }

  @Override
  public MsgPreference selectOne(LambdaQueryWrapper<MsgPreference> wrapper) {
    return msgPreferenceMapper.selectOne(wrapper);
  }

  @Override
  public List<MsgPreference> selectList(LambdaQueryWrapper<MsgPreference> wrapper) {
    return msgPreferenceMapper.selectList(wrapper);
  }
}
