package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.notify.dedup.NotifyDedupService;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.event.MessageSkippedEvent;
import com.njydsz.message.server.event.DomainEventPublisher;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.GuardService;

/**
 * 智能去重 Handler。
 *
 * <p>委托 {@link com.njydsz.message.server.service.core.GuardService#tryDedup(String)}
 * 实现窗口内重复消息跳过发送，底层基于 ydsz-common-safe 的 {@link
 * com.njydsz.common.safe.idempotent.strategy.IdempotentStrategy} 原子去重。
 * 去重 key 由 bizId + receiver + templateCode 拼接而成，
 * 含 channel 时追加 channel 维度（P2-D3 去重精化）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@Order(600)
@RequiredArgsConstructor
public class DedupHandler implements SendHandler {
  /** 去重处理器优先级 */
  private static final int DEDUP_PRIORITY = 600;


  private final GuardService guardService;
  private final MessageMetrics messageMetrics;
  private final DomainEventPublisher domainEventPublisher;
  /**
   * notify 管道去重服务（ObjectProvider 可选注入）。
   *
   * <p>common-notify 未装配或未启用时，仅走消息级去重，保持向后兼容。
   */
  private final ObjectProvider<NotifyDedupService> notifyDedupServiceProvider;

  @Override
  public boolean handle(MessageItemRequestDTO request, SendContext ctx) {
    String dedupKey = buildDedupKey(request);
    if (!StringUtils.hasText(dedupKey)) {
      return true;
    }
    ctx.setDedupKey(dedupKey);
    if (!guardService.tryDedup(dedupKey)) {
      log.info(
          "[DedupHandler] 去重命中,跳过发送: dedupKey={}, receiver={}, templateCode={}, channel={}",
          dedupKey,
          SensitiveUtil.scanAndMask(ctx.getReceiver()),
          request.getTemplateCode(),
          request.getChannel());
      messageMetrics.recordSend(ctx.getChannel(), "DEDUPED", 0);
      // P2-A4: 发布消息被拦截领域事件
      domainEventPublisher.publish(
          new MessageSkippedEvent(
              TenantContextHolder.getTenantId(),
              request.getMessageId(),
              "DEDUP",
              ctx.getChannel(),
              ctx.getBizType()));
      ctx.setErrorResult(MessageSendResultVO.fail(
          ctx.getChannel(),
          MessageExceptionCode.MESSAGE_DUPLICATED.getCode(),
          "消息重复,已忽略",
          "消息重复,已忽略",
          null));
      return false;
    }
    // 双保险：消息级去重通过后，再委托 notify 管道做通知级去重
    if (!checkNotifyDedup(request, ctx)) {
      return false;
    }
    return true;
  }

  /**
   * 通知级去重检查（双保险第二层）。
   *
   * <p>消息级去重通过后，再使用 notify 的 {@link NotifyDedupService} 做一次内容指纹级去重。
   * 若 notify 管道未装配或未启用，跳过；notify 调用异常时降级到纯 message 管道。
   *
   * @param request 消息请求
   * @param ctx     管线上下文
   * @return true 表示非重复允许继续，false 表示被 notify 去重拦截
   */
  private boolean checkNotifyDedup(MessageItemRequestDTO request, SendContext ctx) {
    NotifyDedupService notifyDedupService = notifyDedupServiceProvider.getIfAvailable();
    if (notifyDedupService == null) {
      return true;
    }
    if (!notifyDedupService.isDedupEnabled()) {
      return true;
    }
    try {
      String receiver = ctx.getReceiver();
      String subject = request.getSubject();
      String content = request.getContent();
      if (notifyDedupService.isDuplicate(receiver, subject, content)) {
        log.info(
            "[DedupHandler] notify 管道去重命中,跳过发送: receiver={}, templateCode={}, channel={}",
            SensitiveUtil.scanAndMask(receiver),
            request.getTemplateCode(),
            request.getChannel());
        messageMetrics.recordSend(ctx.getChannel(), "NOTIFY_DEDUPED", 0);
        domainEventPublisher.publish(
            new MessageSkippedEvent(
                TenantContextHolder.getTenantId(),
                request.getMessageId(),
                "NOTIFY_DEDUP",
                ctx.getChannel(),
                ctx.getBizType()));
        ctx.setErrorResult(MessageSendResultVO.fail(
            ctx.getChannel(),
            MessageExceptionCode.MESSAGE_DUPLICATED.getCode(),
            "消息重复,已忽略",
            "消息重复,已忽略",
            null));
        return false;
      }
    } catch (Exception e) {
      log.warn(
          "[DedupHandler] notify 管道去重异常,降级到纯 message 管道: receiver={}, err={}",
          SensitiveUtil.scanAndMask(ctx.getReceiver()),
          e.getMessage());
    }
    return true;
  }

  @Override
  public int order() {
    return DEDUP_PRIORITY;
  }

  /**
   * 构建去重 key：bizId + receiver + templateCode，含 channel 时追加 channel 维度。
   *
   * <p>格式：
   * <ul>
   * <li>有 channel：ydsz:msg:dedup:{bizId}:{receiver}:{templateCode}:{channel}</li>
   * <li>无 channel：ydsz:msg:dedup:{bizId}:{receiver}:{templateCode}</li>
   * </ul>
   *
   * @param request 消息发送请求（取 bizId/receiver/templateCode/channel 字段）
   * @return 去重 key 字符串，bizId 为空时返回 null
   */
  private String buildDedupKey(MessageItemRequestDTO request) {
    if (!StringUtils.hasText(request.getBizId())) {
      return null;
    }
    String receiver = StringUtils.hasText(request.getReceiver()) ? request.getReceiver() : "";
    String templateCode =
        StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : "";
    StringBuilder key = new StringBuilder(MessageConstants.DEDUP_KEY_PREFIX)
        .append(request.getBizId()).append(":")
        .append(receiver).append(":")
        .append(templateCode);
    if (StringUtils.hasText(request.getChannel())) {
      key.append(":").append(request.getChannel());
    }
    return key.toString();
  }
}

