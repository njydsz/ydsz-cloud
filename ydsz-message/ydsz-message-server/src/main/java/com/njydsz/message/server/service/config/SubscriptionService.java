package com.njydsz.message.server.service.config;

import java.util.List;

import com.njydsz.message.domain.dto.SubscriptionUpsertDTO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;

/**
 * 订阅关系 Service
 *
 * <p>封装用户对「主题+通道」维度的订阅/退订语义，是消息中心默认订阅模型的核心入口。 业务调用方通过本服务判断某用户对某主题某通道是否处于"已退订"状态,若已退订则跳过发送。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #upsert} — 新增或更新订阅关系
 *   <li><b>查询</b>：{@link #listByUser} / {@link #listByTopic} — 按用户/主题查询订阅列表
 *   <li><b>判断</b>：{@link #isSubscribed} / {@link #isBlocked} — 用于发送前置检查
 *   <li><b>退订</b>：{@link #unsubscribe} — 用户自助退订,无记录时按 UNSUBSCRIBED 新建(P1-5)
 * </ul>
 *
 * <p><b>默认订阅语义：</b>系统中"未配置"≠"未订阅"。
 *
 * <ul>
 *   <li>无记录或 {@code SUBSCRIBED} → 视为已订阅（允许发送）
 *   <li>仅当存在 {@code UNSUBSCRIBED} 记录时视为退订（拦截发送）
 * </ul>
 *
 * <p><b>事务：</b>{@link #unsubscribe} 开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.domain.vo.MsgSubscriptionVO 订阅VO
 * @see UnsubscribeService 退订中心服务(基于签名 token 的一键退订)
 * @see PreferenceService 用户消息偏好服务
 */
public interface SubscriptionService {

  /**
   * 新增或更新订阅关系
   *
   * @param dto 订阅参数
   * @return 订阅实体
   */
  MsgSubscriptionVO upsert(SubscriptionUpsertDTO dto);

  /**
   * 查询用户所有订阅
   *
   * @param userId 用户 ID
   * @return 订阅列表
   */
  List<MsgSubscriptionVO> listByUser(String userId);

  /**
   * 按主题 + 通道查询订阅列表
   *
   * @param topicCode 主题编码
   * @param channel 通道
   * @return 订阅列表
   */
  List<MsgSubscriptionVO> listByTopic(String topicCode, String channel);

  /**
   * 判断用户是否已订阅指定主题 + 通道
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 通道
   * @return true 表示已订阅
   */
  boolean isSubscribed(String userId, String topicCode, String channel);

  /**
   * 判断用户是否已退订(拦截发送)。默认订阅语义:无记录或 SUBSCRIBED 返回 false, 仅当存在 UNSUBSCRIBED 记录时返回 true。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 通道
   * @return true 表示用户已退订,应拦截发送
   */
  boolean isBlocked(String userId, String topicCode, String channel);

  /**
   * 退订指定主题 + 通道
   *
   * <p>P1-5: 无订阅记录时新建 UNSUBSCRIBED 记录(修复默认订阅语义下的 latent bug), 并返回退订后的订阅实体。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 通道
   * @return 退订后的订阅实体
   */
  MsgSubscriptionVO unsubscribe(String userId, String topicCode, String channel);
}
