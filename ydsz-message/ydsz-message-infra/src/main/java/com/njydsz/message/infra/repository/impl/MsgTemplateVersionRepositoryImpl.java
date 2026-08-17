package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.template.MsgTemplateVersion;
import com.njydsz.message.infra.mapper.template.MsgTemplateVersionMapper;
import com.njydsz.message.infra.repository.MsgTemplateVersionRepository;

/**
 * 消息模板版本历史 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgTemplateVersionMapper} 实现 {@link MsgTemplateVersionRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTemplateVersionRepositoryImpl implements MsgTemplateVersionRepository {

  private final MsgTemplateVersionMapper msgTemplateVersionMapper;

  @Override
  public int insert(MsgTemplateVersion entity) {
    return msgTemplateVersionMapper.insert(entity);
  }

  @Override
  public List<MsgTemplateVersion> selectList(LambdaQueryWrapper<MsgTemplateVersion> wrapper) {
    return msgTemplateVersionMapper.selectList(wrapper);
  }

  @Override
  public MsgTemplateVersion selectOne(LambdaQueryWrapper<MsgTemplateVersion> wrapper) {
    return msgTemplateVersionMapper.selectOne(wrapper);
  }
}
