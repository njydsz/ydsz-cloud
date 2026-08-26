package com.njydsz.message.server.service.core;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.njydsz.message.domain.dto.MessageFeedbackDTO;
import com.njydsz.message.domain.vo.MsgFeedbackVO;

/**
 * 消息质量反馈 Service
 *
 * <p>用户对收到的消息进行评分(1-5 星)和文字反馈,用于:
 *
 * <ul>
 *   <li><b>评估消息推送质量</b>：用户满意度分析
 *   <li><b>优化消息内容</b>：基于反馈调整模板
 *   <li><b>智能防骚扰</b>：用户多次差评后自动降频
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>提交反馈</b>：{@link #submitFeedback}
 *   <li><b>评分统计</b>：{@link #getAverageRating} / {@link #getAverageRatingByChannel}
 *   <li><b>分页查询</b>：{@link #pageFeedback}
 *   <li><b>降频判断</b>：{@link #shouldReduceFrequency} — 基于最近反馈评分判断是否需要降频
 * </ul>
 *
 * <p><b>降频阈值：</b>由 {@code ydsz.message.feedback.reduce-threshold} 配置,默认 3.0。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.vo.MsgFeedbackVO 反馈VO
 * @see RateLimitService 限流服务(降频会调整 rate-limit 阈值)
 */
public interface MessageFeedbackService {

  /**
   * 提交消息反馈。
   *
   * @param dto 反馈请求
   * @return 反馈记录 ID
   */
  String submitFeedback(MessageFeedbackDTO dto);

  /**
   * 查询用户消息反馈评分（平均值）。
   *
   * @param userId 用户 ID
   * @return 平均评分（1-5），无反馈返回 0
   */
  double getAverageRating(String userId);

  /**
   * 查询消息反馈统计（按通道）。
   *
   * @param channel 通道
   * @return 平均评分
   */
  double getAverageRatingByChannel(String channel);

  /**
   * 分页查询反馈记录。
   *
   * @param page 页码
   * @param size 每页大小
   * @param channel 通道（可选筛选）
   * @param userId 用户 ID（可选筛选）
   * @return 分页结果
   */
  Page<MsgFeedbackVO> pageFeedback(int page, int size, String channel, String userId);

  /**
   * 检查用户是否需要降频（基于最近反馈评分）。
   *
   * <p>如果用户最近 N 条反馈平均分低于阈值，返回 true， 建议降低该用户的消息推送频率。
   *
   * @param userId 用户 ID
   * @return true 表示建议降频
   */
  boolean shouldReduceFrequency(String userId);
}
