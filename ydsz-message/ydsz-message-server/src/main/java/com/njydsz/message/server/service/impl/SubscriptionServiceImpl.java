package com.njydsz.message.server.service.impl.config;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.SubscriptionUpsertDTO;
import com.njydsz.message.domain.enums.config.SubscriptionStatusEnum;
import com.njydsz.message.domain.query.MsgSubscriptionQuery;
import com.njydsz.message.domain.repository.MsgSubscriptionRepository;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.SubscriptionService;

/**
 * 消息订阅服务实现。
 *
 * <p>用户/角色/部门维度的消息订阅管理 ({@code ydsz_msg_subscription})：
 *
 * <p>订阅模板、订阅租户、订阅频率、订阅标签。
 *
 * <p>发布时根据订阅关系生成接收人列表。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

  /** 订阅关系 Repository */
  private final MsgSubscriptionRepository msgSubscriptionRepository;

  /**
   * 新增或更新订阅关系
   *
   * <p>按 (userId, topicCode, channel) 唯一约束 upsert。新增时插入，已存在时更新状态。
   *
   * @param dto 订阅 upsert 参数
   * @return 落库后的订阅记录
   * @throws SysException 必填字段为空时抛出
   */
  @Override
  public MsgSubscriptionVO upsert(SubscriptionUpsertDTO dto) {
    if (dto == null
        || !StringUtils.hasText(dto.getUserId())
        || !StringUtils.hasText(dto.getTopicCode())
        || !StringUtils.hasText(dto.getChannel())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID、主题编码与通道不能为空")
          .build();
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setUserId(dto.getUserId());
    query.setTopicCode(dto.getTopicCode());
    query.setChannel(dto.getChannel());
    MsgSubscriptionVO existing = msgSubscriptionRepository.findOne(query).orElse(null);
    String status =
        StringUtils.hasText(dto.getStatus())
            ? dto.getStatus()
            : SubscriptionStatusEnum.SUBSCRIBED.name();
    if (existing == null) {
      MsgSubscriptionVO entity = new MsgSubscriptionVO();
      entity.setUserId(dto.getUserId());
      entity.setTopicCode(dto.getTopicCode());
      entity.setChannel(dto.getChannel());
      entity.setStatus(status);
      entity.setRoleScope(dto.getRoleScope());
      entity.setExtra(dto.getExtra());
      entity.setTenantId(TenantContextHolder.getTenantId());
      msgSubscriptionRepository.save(entity);
      log.info(
          "[Subscription] 新建订阅: user={} topic={} channel={}",
          dto.getUserId(),
          dto.getTopicCode(),
          dto.getChannel());
      return entity;
    }
    existing.setStatus(status);
    existing.setRoleScope(dto.getRoleScope());
    existing.setExtra(dto.getExtra());
    // P1-5: 恢复订阅时清空退订时间;退订时记录退订时间
    if (SubscriptionStatusEnum.SUBSCRIBED.name().equals(status)) {
      existing.setUnsubscribedAt(null);
    } else if (SubscriptionStatusEnum.UNSUBSCRIBED.name().equals(status)
        && existing.getUnsubscribedAt() == null) {
      existing.setUnsubscribedAt(LocalDateTime.now());
    }
    msgSubscriptionRepository.update(existing);
    return existing;
  }

  /**
   * 查询指定用户的所有订阅记录
   *
   * @param userId 用户 ID
   * @return 订阅记录列表（按创建时间倒序）；userId 为空时返回空列表
   */
  @Override
  public List<MsgSubscriptionVO> listByUser(String userId) {
    if (!StringUtils.hasText(userId)) {
      return List.of();
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setUserId(userId);
    query.addDescOrder("created_at");
    return msgSubscriptionRepository.findList(query);
  }

  /**
   * 查询指定主题下的活跃订阅列表
   *
   * @param topicCode 主题编码
   * @param channel 消息通道（可空，空时查全部通道）
   * @return 订阅状态为 SUBSCRIBED 的记录列表
   */
  @Override
  public List<MsgSubscriptionVO> listByTopic(String topicCode, String channel) {
    if (!StringUtils.hasText(topicCode)) {
      return List.of();
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setTopicCode(topicCode);
    query.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
    if (StringUtils.hasText(channel)) {
      query.setChannel(channel);
    }
    return msgSubscriptionRepository.findList(query);
  }

  /**
   * 判断用户是否已订阅指定主题与通道
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道（可空）
   * @return true 表示已订阅（SUBSCRIBED 状态）
   */
  @Override
  public boolean isSubscribed(String userId, String topicCode, String channel) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
      return false;
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setUserId(userId);
    query.setTopicCode(topicCode);
    if (StringUtils.hasText(channel)) {
      query.setChannel(channel);
    }
    query.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
    return msgSubscriptionRepository.count(query) > 0;
  }

  /**
   * 判断用户是否已退订指定主题与通道
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道（可空）
   * @return true 表示已退订（UNSUBSCRIBED 状态）
   */
  @Override
  public boolean isBlocked(String userId, String topicCode, String channel) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(topicCode)) {
      return false;
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setUserId(userId);
    query.setTopicCode(topicCode);
    if (StringUtils.hasText(channel)) {
      query.setChannel(channel);
    }
    query.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
    return msgSubscriptionRepository.count(query) > 0;
  }

  /**
   * 执行退订操作
   *
   * <p>将指定用户+主题+通道的订阅状态更新为 UNSUBSCRIBED。 无记录时新建 UNSUBSCRIBED 记录（防止默认订阅语义下 isBlocked 返回 false）。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道
   * @return 更新后的订阅记录
   * @throws SysException 必填字段为空时抛出
   */
  @Override
  public MsgSubscriptionVO unsubscribe(String userId, String topicCode, String channel) {
    if (!StringUtils.hasText(userId)
        || !StringUtils.hasText(topicCode)
        || !StringUtils.hasText(channel)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID、主题编码与通道不能为空")
          .build();
    }
    MsgSubscriptionQuery query = new MsgSubscriptionQuery();
    query.setUserId(userId);
    query.setTopicCode(topicCode);
    query.setChannel(channel);
    MsgSubscriptionVO existing = msgSubscriptionRepository.findOne(query).orElse(null);
    if (existing == null) {
      // P1-5: 无订阅记录时也要创建 UNSUBSCRIBED 记录,否则 isBlocked 永远返回 false,
      // 用户点击退订后仍会被发送(默认订阅语义)。修复此 latent bug。
      MsgSubscriptionVO entity = new MsgSubscriptionVO();
      entity.setUserId(userId);
      entity.setTopicCode(topicCode);
      entity.setChannel(channel);
      entity.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
      entity.setUnsubscribedAt(LocalDateTime.now());
      entity.setTenantId(TenantContextHolder.getTenantId());
      msgSubscriptionRepository.save(entity);
      log.info("[Subscription] 退订(新建记录): user={} topic={} channel={}", userId, topicCode, channel);
      return entity;
    }
    existing.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
    existing.setUnsubscribedAt(LocalDateTime.now());
    msgSubscriptionRepository.update(existing);
    return existing;
  }
}
