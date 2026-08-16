package com.njydsz.message.server.service.impl.core;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.core.MessageFeedbackDTO;
import com.njydsz.message.domain.entity.config.MsgFeedback;
import com.njydsz.message.infra.mapper.config.MsgFeedbackMapper;
import com.njydsz.message.server.service.core.MessageFeedbackService;

/**
 * 消息反馈服务实现。
 *
 * <p>收集用户对消息的反馈：已读/未读/点击/有用/无用/投诉。
 *
 * <p>基于反馈数据训练送达最佳时机、内容优化模型（与 {@code DeliveryTimeOptimizer} 配合）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFeedbackServiceImpl implements MessageFeedbackService {

  /** 消息反馈 Mapper */
  private final MsgFeedbackMapper msgFeedbackMapper;

  /** 降频判断窗口：最近多少条反馈 */
  private static final int FREQ_CHECK_WINDOW = 5;

  /** 降频阈值：平均分低于此值则建议降频 */
  private static final double FREQ_REDUCTION_THRESHOLD = 2.5;

  /**
   * {@inheritDoc}
   *
   * <p>校验 msgId/notificationId、userId、rating（1-5）后落库，tenantId 从 {@link TenantContext} 获取。
   *
   * @throws SysException 当 dto 为空、消息 ID 为空、用户 ID 为空或评分不在 1-5 范围时抛出
   */
  @Override
  public String submitFeedback(MessageFeedbackDTO dto) {
    if (dto == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("反馈内容不能为空")
          .build();
    }
    if (!StringUtils.hasText(dto.getMsgId()) && !StringUtils.hasText(dto.getNotificationId())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("消息 ID 或通知 ID 不能为空")
          .build();
    }
    if (!StringUtils.hasText(dto.getUserId())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("用户 ID 不能为空")
          .build();
    }
    if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("评分必须在 1-5 之间")
          .build();
    }

    MsgFeedback feedback = new MsgFeedback();
    feedback.setMsgId(dto.getMsgId());
    feedback.setNotificationId(dto.getNotificationId());
    feedback.setUserId(dto.getUserId());
    feedback.setRating(dto.getRating());
    feedback.setFeedbackType(dto.getFeedbackType());
    feedback.setContent(dto.getContent());
    feedback.setTenantId(TenantContextHolder.getTenantId());

    // 通道和业务类型由前端或上游传入，此处不强制补全

    msgFeedbackMapper.insert(feedback);
    log.info(
        "[Feedback] 用户反馈已提交: userId={} msgId={} rating={} type={}",
        dto.getUserId(),
        dto.getMsgId(),
        dto.getRating(),
        dto.getFeedbackType());
    return feedback.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>查询用户最近 100 条反馈的平均评分，无数据时返回 0。
   *
   * @param userId 用户 ID
   * @return 平均评分（0-5），无反馈时返回 0
   */
  @Override
  public double getAverageRating(String userId) {
    if (!StringUtils.hasText(userId)) {
      return 0;
    }
    List<MsgFeedback> feedbacks =
        msgFeedbackMapper.selectList(
            new LambdaQueryWrapper<MsgFeedback>()
                .eq(MsgFeedback::getUserId, userId)
                .orderByDesc(MsgFeedback::getCreatedAt)
                .last("LIMIT 100"));
    if (feedbacks.isEmpty()) {
      return 0;
    }
    return feedbacks.stream()
        .filter(f -> f.getRating() != null)
        .mapToInt(MsgFeedback::getRating)
        .average()
        .orElse(0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>查询指定通道最近 1000 条反馈的平均评分，无数据时返回 0。
   *
   * @param channel 通道类型
   * @return 平均评分（0-5），无反馈时返回 0
   */
  @Override
  public double getAverageRatingByChannel(String channel) {
    if (!StringUtils.hasText(channel)) {
      return 0;
    }
    List<MsgFeedback> feedbacks =
        msgFeedbackMapper.selectList(
            new LambdaQueryWrapper<MsgFeedback>()
                .eq(MsgFeedback::getChannel, channel)
                .orderByDesc(MsgFeedback::getCreatedAt)
                .last("LIMIT 1000"));
    if (feedbacks.isEmpty()) {
      return 0;
    }
    return feedbacks.stream()
        .filter(f -> f.getRating() != null)
        .mapToInt(MsgFeedback::getRating)
        .average()
        .orElse(0);
  }

  /**
   * {@inheritDoc}
   *
   * <p>支持按 channel 和 userId 过滤，按创建时间降序排列。
   */
  @Override
  public Page<MsgFeedback> pageFeedback(int page, int size, String channel, String userId) {
    Page<MsgFeedback> p = new Page<>(page, size);
    LambdaQueryWrapper<MsgFeedback> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.hasText(channel)) {
      wrapper.eq(MsgFeedback::getChannel, channel);
    }
    if (StringUtils.hasText(userId)) {
      wrapper.eq(MsgFeedback::getUserId, userId);
    }
    wrapper.orderByDesc(MsgFeedback::getCreatedAt);
    return msgFeedbackMapper.selectPage(p, wrapper);
  }

  /**
   * {@inheritDoc}
   *
   * <p>取用户最近 {@value #FREQ_CHECK_WINDOW} 条反馈，平均分低于 {@value #FREQ_REDUCTION_THRESHOLD} 时返回 true。
   * 反馈条数不足窗口值时不降频。
   *
   * @param userId 用户 ID
   * @return true 表示建议降低对该用户的消息发送频率
   */
  @Override
  public boolean shouldReduceFrequency(String userId) {
    if (!StringUtils.hasText(userId)) {
      return false;
    }
    List<MsgFeedback> recentFeedbacks =
        msgFeedbackMapper.selectList(
            new LambdaQueryWrapper<MsgFeedback>()
                .eq(MsgFeedback::getUserId, userId)
                .orderByDesc(MsgFeedback::getCreatedAt)
                .last("LIMIT " + FREQ_CHECK_WINDOW));
    if (recentFeedbacks.size() < FREQ_CHECK_WINDOW) {
      return false; // 反馈不足，不降频
    }
    double avgRating =
        recentFeedbacks.stream()
            .filter(f -> f.getRating() != null)
            .mapToInt(MsgFeedback::getRating)
            .average()
            .orElse(5.0);
    boolean shouldReduce = avgRating < FREQ_REDUCTION_THRESHOLD;
    if (shouldReduce) {
      log.info(
          "[Feedback] 用户建议降频: userId={} avgRating={} threshold={}",
          userId,
          avgRating,
          FREQ_REDUCTION_THRESHOLD);
    }
    return shouldReduce;
  }
}
