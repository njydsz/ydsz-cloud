package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgFeedback;
import com.njydsz.message.infra.mapper.config.MsgFeedbackMapper;
import com.njydsz.message.infra.repository.MsgFeedbackRepository;

/**
 * 消息用户反馈 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgFeedbackMapper} 实现 {@link MsgFeedbackRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgFeedbackRepositoryImpl implements MsgFeedbackRepository {

  private final MsgFeedbackMapper msgFeedbackMapper;

  @Override
  public int insert(MsgFeedback entity) {
    return msgFeedbackMapper.insert(entity);
  }

  @Override
  public List<MsgFeedback> selectList(LambdaQueryWrapper<MsgFeedback> wrapper) {
    return msgFeedbackMapper.selectList(wrapper);
  }

  @Override
  public Page<MsgFeedback> selectPage(Page<MsgFeedback> page, LambdaQueryWrapper<MsgFeedback> wrapper) {
    return msgFeedbackMapper.selectPage(page, wrapper);
  }
}
