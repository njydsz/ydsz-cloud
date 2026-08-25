package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgFeedbackQuery;
import com.njydsz.message.domain.repository.MsgFeedbackRepository;
import com.njydsz.message.domain.vo.MsgFeedbackVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgFeedback;
import com.njydsz.message.infra.mapper.config.MsgFeedbackMapper;

/**
 * 消息用户反馈仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgFeedbackRepository} 接口，封装 MsgFeedbackMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgFeedbackRepositoryImpl implements MsgFeedbackRepository {

  private final MsgFeedbackMapper msgFeedbackMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgFeedbackVO vo) {
    MsgFeedback entity = voToEntity(vo);
    return msgFeedbackMapper.insert(entity) > 0;
  }

  @Override
  public List<MsgFeedbackVO> findList(MsgFeedbackQuery query) {
    QueryWrapper<MsgFeedback> wrapper = buildWrapper(query);
    return converter.feedbackListToVO(msgFeedbackMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<MsgFeedbackVO>> findPage(MsgFeedbackQuery query) {
    Page<MsgFeedback> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgFeedback> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgFeedback> entityPage = msgFeedbackMapper.selectPage(page, wrapper);
    List<MsgFeedbackVO> vos = converter.feedbackListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  private QueryWrapper<MsgFeedback> buildWrapper(MsgFeedbackQuery query) {
    QueryWrapper<MsgFeedback> wrapper = new QueryWrapper<>();
    if (query.getMsgId() != null && !query.getMsgId().isBlank()) {
      wrapper.eq("msg_id", query.getMsgId());
    }
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq("user_id", query.getUserId());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getFeedbackType() != null && !query.getFeedbackType().isBlank()) {
      wrapper.eq("feedback_type", query.getFeedbackType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgFeedback voToEntity(MsgFeedbackVO vo) {
    if (vo == null) {
      return null;
    }
    MsgFeedback entity = new MsgFeedback();
    entity.setId(vo.getId());
    entity.setMsgId(vo.getMsgId());
    entity.setNotificationId(vo.getNotificationId());
    entity.setUserId(vo.getUserId());
    entity.setChannel(vo.getChannel());
    entity.setBizType(vo.getBizType());
    entity.setRating(vo.getRating());
    entity.setFeedbackType(vo.getFeedbackType());
    entity.setContent(vo.getContent());
    return entity;
  }
}
