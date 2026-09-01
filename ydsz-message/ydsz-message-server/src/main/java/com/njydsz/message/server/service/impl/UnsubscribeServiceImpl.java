package com.njydsz.message.server.service.impl.config;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.dto.UnsubscribeQueryDTO;
import com.njydsz.message.domain.enums.config.SubscriptionStatusEnum;
import com.njydsz.message.domain.query.MsgSubscriptionQuery;
import com.njydsz.message.domain.repository.MsgSubscriptionRepository;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.config.SubscriptionService;
import com.njydsz.message.server.service.config.UnsubscribeService;
import com.njydsz.message.server.token.UnsubscribeTokenPayload;
import com.njydsz.message.server.token.UnsubscribeTokenUtil;

/**
 * 退订服务实现。
 *
 * <p>管理用户对模板/渠道/标签的退订关系：URL 退订（一键退订链接）、
 *
 * <p>回复关键字退订（短信）、IM 退订。
 *
 * <p>退订记录作为通知偏好的「强约束」覆盖，发送前自动过滤。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnsubscribeServiceImpl implements UnsubscribeService {

  /** 退订 token 工具（签名/校验） */
  private final UnsubscribeTokenUtil unsubscribeTokenUtil;

  /** 订阅关系服务（状态变更） */
  private final SubscriptionService subscriptionService;

  /** 订阅关系 Repository（退订查询） */
  private final MsgSubscriptionRepository msgSubscriptionRepository;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  /**
   * 生成退订 token
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道
   * @return 签名后的退订 token
   */
  @Override
  public String generateToken(String userId, String topicCode, String channel) {
    return unsubscribeTokenUtil.generate(userId, topicCode, channel);
  }

  /**
   * 预览退订 token 信息（不执行退订）
   *
   * @param token 退订 token
   * @return token 载荷（userId、topicCode、channel、过期时间）
   */
  @Override
  public UnsubscribeTokenPayload previewToken(String token) {
    return unsubscribeTokenUtil.parseAndVerify(token);
  }

  /**
   * 通过退订 token 执行退订
   *
   * <p>校验 token 签名与有效期后，调用 SubscriptionService 更新订阅状态为 UNSUBSCRIBED。
   *
   * @param token 退订 token
   * @return 更新后的订阅记录
   * @throws SysException 退订中心关闭或 token 无效时抛出
   */
  @Override
  public MsgSubscriptionVO unsubscribeByToken(String token) {
    if (!messageProperties.getUnsubscribe().isEnabled()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("退订中心已关闭")
          .build();
    }
    UnsubscribeTokenPayload payload = unsubscribeTokenUtil.parseAndVerify(token);
    log.info(
        "[Unsubscribe] token 退订: user={} topic={} channel={}",
        payload.getUserId(),
        payload.getTopicCode(),
        payload.getChannel());
    return subscriptionService.unsubscribe(
        payload.getUserId(), payload.getTopicCode(), payload.getChannel());
  }

  /**
   * 分页查询已退订的订阅记录
   *
   * @param query 查询条件（userId、topicCode、channel）
   * @return 分页结果
   */
  @Override
  public PageResponse<List<MsgSubscriptionVO>> pageUnsubscribed(UnsubscribeQueryDTO query) {
    if (query == null) {
      query = new UnsubscribeQueryDTO();
    }
    MsgSubscriptionQuery subscriptionQuery = new MsgSubscriptionQuery();
    subscriptionQuery.setPageNum(query.getPageNum());
    subscriptionQuery.setPageSize(Math.min(query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    subscriptionQuery.setStatus(SubscriptionStatusEnum.UNSUBSCRIBED.name());
    if (StringUtils.hasText(query.getUserId())) {
      subscriptionQuery.setUserId(query.getUserId());
    }
    if (StringUtils.hasText(query.getTopicCode())) {
      subscriptionQuery.setTopicCode(query.getTopicCode());
    }
    if (StringUtils.hasText(query.getChannel())) {
      subscriptionQuery.setChannel(query.getChannel());
    }
    return msgSubscriptionRepository.findPage(subscriptionQuery);
  }

  /**
   * 按用户+主题+通道三元组退订（带退订原因）。
   *
   * <p>供管理后台或用户自助退订场景使用，内部委托 {@link SubscriptionService#unsubscribe}。 退订原因供运营分析，不影响退订逻辑。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道
   * @param reason 退订原因（可选，供运营分析）
   * @return 退订后的订阅记录
   */
  @Override
  public MsgSubscriptionVO unsubscribe(String userId, String topicCode, String channel, String reason) {
    if (StringUtils.hasText(reason)) {
      log.info(
          "[Unsubscribe] 退订原因: user={} topic={} channel={} reason={}",
          userId, topicCode, channel, reason);
    }
    return subscriptionService.unsubscribe(userId, topicCode, channel);
  }

  /**
   * 恢复订阅
   *
   * <p>将指定用户+主题+通道的订阅状态恢复为 SUBSCRIBED。 无记录时新建 SUBSCRIBED 记录；已订阅则跳过。
   *
   * @param userId 用户 ID
   * @param topicCode 主题编码
   * @param channel 消息通道
   * @throws SysException 参数为空时抛出
   */
  @Override
  public void resubscribe(String userId, String topicCode, String channel) {
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
    Optional<MsgSubscriptionVO> existing = msgSubscriptionRepository.findOne(query);
    if (existing.isEmpty()) {
      // 无记录时直接新建 SUBSCRIBED 记录
      MsgSubscriptionVO vo = new MsgSubscriptionVO();
      vo.setUserId(userId);
      vo.setTopicCode(topicCode);
      vo.setChannel(channel);
      vo.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
      msgSubscriptionRepository.save(vo);
      log.info("[Unsubscribe] 恢复订阅(新建): user={} topic={} channel={}", userId, topicCode, channel);
      return;
    }
    MsgSubscriptionVO vo = existing.get();
    if (SubscriptionStatusEnum.SUBSCRIBED.name().equals(vo.getStatus())) {
      return;
    }
    vo.setStatus(SubscriptionStatusEnum.SUBSCRIBED.name());
    vo.setUnsubscribedAt(null);
    msgSubscriptionRepository.update(vo);
    log.info("[Unsubscribe] 恢复订阅: user={} topic={} channel={}", userId, topicCode, channel);
  }
}
