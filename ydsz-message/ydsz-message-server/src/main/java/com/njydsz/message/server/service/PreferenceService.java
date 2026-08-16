package com.njydsz.message.server.service.config;

import com.njydsz.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgPreference;
import java.util.List;

/**
 * 用户消息偏好 Service
 *
 * <p>管理用户对各通道(站内/短信/邮件/推送/IM)的"接收/不接收"偏好, 在消息发送前由 {@code MessageService} 调用 {@link #getByUser}
 * 判断是否允许该通道发送。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #upsert} / {@link #delete}
 *   <li><b>查询</b>：{@link #getByUser} / {@link #listByUser}
 * </ul>
 *
 * <p><b>三元组唯一约束：</b>{@code (userId, channel, bizType)} 唯一。
 *
 * <ul>
 *   <li>{@code channel} — 通道(IN_APP/SMS/EMAIL/PUSH/IM)
 *   <li>{@code bizType} — 业务类型(用于按业务线粒度配置,如 WORKFLOW/PROJECT/CONTRACT 等)
 * </ul>
 *
 * <p><b>默认值：</b>用户无偏好记录时视为"全部接收"(与 {@link SubscriptionService} 的默认订阅语义一致)。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.entity.config.MsgPreference 偏好实体
 * @see SubscriptionService 订阅关系服务
 */
public interface PreferenceService {

  /**
   * 新增或更新用户偏好
   *
   * @param dto 偏好参数
   * @return 偏好实体
   */
  MsgPreference upsert(PreferenceUpsertDTO dto);

  /**
   * 按用户 + 通道 + 业务类型查询偏好
   *
   * @param userId 用户 ID
   * @param channel 通道
   * @param bizType 业务类型
   * @return 偏好实体
   */
  MsgPreference getByUser(String userId, String channel, String bizType);

  /**
   * 查询用户所有偏好
   *
   * @param userId 用户 ID
   * @return 偏好列表
   */
  List<MsgPreference> listByUser(String userId);

  /**
   * 删除偏好(逻辑删除)
   *
   * @param id 偏好 ID
   */
  void delete(String id);
}
