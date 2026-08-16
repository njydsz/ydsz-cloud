package com.njydsz.message.server.service.core;

import com.njydsz.message.domain.dto.core.UserReachProfileDTO;
import java.util.List;

/**
 * 智能触达策略 Service
 *
 * <p>基于用户画像(通道活跃度、历史打开率/点击率、免打扰偏好、时区等)动态选择最优通道和 发送时机,提升触达率和用户体验,降低无效推送对用户的打扰。
 *
 * <p><b>评分维度：</b>
 *
 * <ul>
 *   <li>通道活跃度(用户在该通道的历史活跃程度)
 *   <li>历史打开率/点击率
 *   <li>免打扰时段过滤
 *   <li>时区感知(确保在用户活跃时段发送)
 *   <li>通道成本(优先使用低成本高触达通道)
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>画像</b>：{@link #getProfile} — 获取/构建用户触达画像
 *   <li><b>通道选择</b>：{@link #selectOptimalChannels} — 排序后的通道列表(最优在前)
 *   <li><b>免打扰</b>：{@link #isInDndPeriod} — 是否在用户免打扰时段
 *   <li><b>发送时间</b>：{@link #getOptimalTimeWindow} — 用户最优活跃时段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.dto.core.UserReachProfileDTO 用户触达画像 DTO
 * @see ReachStrategyService 智能触达主服务
 */
public interface ReachStrategyService {

  /**
   * 获取用户触达画像。
   *
   * @param userId 用户 ID
   * @return 画像 DTO；无数据时返回默认画像
   */
  UserReachProfileDTO getProfile(String userId);

  /**
   * 智能选择最优通道。
   *
   * <p>综合评分各通道的活跃度、打开率、成本和用户偏好， 返回排序后的通道列表（最优在前）。
   *
   * @param userId 用户 ID
   * @param channels 候选通道列表
   * @param bizType 业务类型
   * @return 排序后的通道列表
   */
  List<String> selectOptimalChannels(String userId, List<String> channels, String bizType);

  /**
   * 判断当前时间是否在用户免打扰时段。
   *
   * @param userId 用户 ID
   * @return true 表示在免打扰时段
   */
  boolean isInDndPeriod(String userId);

  /**
   * 获取用户最优发送时间窗口。
   *
   * <p>基于历史活跃时段分析，返回推荐的发送时间范围。
   *
   * @param userId 用户 ID
   * @return 时间窗口描述（如 "09:00-21:00"）
   */
  String getOptimalTimeWindow(String userId);
}
