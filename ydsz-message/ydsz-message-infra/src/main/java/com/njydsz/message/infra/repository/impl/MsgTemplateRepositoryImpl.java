package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.infra.mapper.template.MsgTemplateMapper;
import com.njydsz.message.infra.repository.MsgTemplateRepository;

/**
 * 消息模板 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgTemplateMapper} 实现 {@link MsgTemplateRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTemplateRepositoryImpl implements MsgTemplateRepository {

  private final MsgTemplateMapper msgTemplateMapper;

  @Override
  public int insert(MsgTemplate entity) {
    return msgTemplateMapper.insert(entity);
  }

  @Override
  public MsgTemplate selectById(String id) {
    return msgTemplateMapper.selectById(id);
  }

  @Override
  public int updateById(MsgTemplate entity) {
    return msgTemplateMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return msgTemplateMapper.deleteById(id);
  }

  @Override
  public MsgTemplate selectOne(LambdaQueryWrapper<MsgTemplate> wrapper) {
    return msgTemplateMapper.selectOne(wrapper);
  }

  @Override
  public Page<MsgTemplate> selectPage(Page<MsgTemplate> page, LambdaQueryWrapper<MsgTemplate> wrapper) {
    return msgTemplateMapper.selectPage(page, wrapper);
  }
}
