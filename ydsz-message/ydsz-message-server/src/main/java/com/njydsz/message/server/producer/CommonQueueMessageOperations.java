package com.njydsz.message.server.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.common.queue.compress.MessageCompressor;
import com.njydsz.common.queue.constant.YdszMessageTopics;
import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.enums.QueueType;
import com.njydsz.common.queue.queue.IMessageQueue;
import com.njydsz.common.queue.queue.IMessageQueueProvider;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.service.TemplateService;

/**
 * 基于 common-queue 抽象的消息队列操作实现。
 *
 * <p>统一通过 common-queue 的 {@link IMessagePublisher} 发送消息，底层可切换 RocketMQ / Kafka / RabbitMQ。
 * 当前为 message 模块默认的 MQ 发送实现（优先于任何 {@link MessageQueueOperations} 的其他实现）。
 *
 * <p>特性：
 * <ul>
 *   <li>同步发送：压缩 payload、携带 priority header、记录发送日志</li>
 *   <li>异步发送：标记 async 意图，底层引擎支持时自然异步</li>
 *   <li>事务消息：发送前校验通道/模板，校验通过后以同步方式投递</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(IMessageQueueProvider.class)
@ConditionalOnMissingBean(MessageQueueOperations.class)
public class CommonQueueMessageOperations implements MessageQueueOperations {

  private final IMessageQueueProvider queueProvider;
  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final TemplateService templateService;
  private final ChannelRouter channelRouter;

  private final IMessagePublisher publisher;

  /**
   * 构造方法，通过 {@link IMessageQueueProvider} 创建 publisher。
   *
   * @param queueProviderProvider 消息队列提供者（用于创建 IMessageQueue 实例）
   * @param snowflakeIdGenerator 分布式 ID 生成器（用于消息 ID 兜底生成）
   * @param templateService 模板服务（事务消息校验）
   * @param channelRouter 通道路由器（事务消息校验）
   */
  public CommonQueueMessageOperations(
      ObjectProvider<IMessageQueueProvider> queueProviderProvider,
      SnowflakeIdGenerator snowflakeIdGenerator,
      TemplateService templateService,
      ChannelRouter channelRouter) {
    IMessageQueueProvider provider = queueProviderProvider.getIfAvailable();
    if (provider == null) {
      throw new IllegalStateException("IMessageQueueProvider 未配置，无法使用 common-queue 抽象");
    }
    this.queueProvider = provider;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    this.templateService = templateService;
    this.channelRouter = channelRouter;
    IMessageQueue queue = provider.createMessageQueue(QueueType.ROCKET);
    this.publisher = queue.createPublisher(YdszMessageTopics.TOPIC_MESSAGE);
    log.info("[CommonQueueMQ] 使用 common-queue 抽象发送消息, topic={}", YdszMessageTopics.TOPIC_MESSAGE);
  }

  @Override
  public String syncSend(MessageItemRequestDTO req) {
    if (req == null) {
      throw BusinessException.builder().key("message.request.required").build();
    }
    ensureMessageId(req);
    String payload = MessageCompressor.compressIfNeeded(YdszJson.toJson(req));
    QueueMessage message = QueueMessage.of(payload);
    message.addHeader("messageId", req.getMessageId());
    message.addHeader("channel", req.getChannel());
    message.addHeader("priority", req.getPriority() != null ? req.getPriority() : "NORMAL");
    message.addHeader("topic", YdszMessageTopics.TOPIC_MESSAGE);
    publisher.publish(message);
    log.info(
        "[CommonQueueMQ] syncSend OK: messageId={} channel={} priority={}",
        req.getMessageId(),
        req.getChannel(),
        req.getPriority());
    return req.getMessageId();
  }

  @Override
  public void asyncSend(MessageItemRequestDTO req) {
    if (req == null) {
      throw BusinessException.builder().key("message.request.required").build();
    }
    log.info("[CommonQueueMQ] asyncSend 开始: messageId={} channel={}", req.getMessageId(), req.getChannel());
    syncSend(req);
  }

  @Override
  public String sendTransactionMessage(MessageItemRequestDTO req) {
    if (req == null) {
      throw BusinessException.builder().key("message.request.required").build();
    }
    String reason = validateRequest(req);
    if (reason != null) {
      log.warn("[CommonQueueMQ] 事务消息校验失败，拒绝发送: messageId={} reason={}", req.getMessageId(), reason);
      throw BusinessException.builder().key("message.rocketmq.transaction.failed").build();
    }
    log.info("[CommonQueueMQ] 事务消息校验通过，执行同步发送: messageId={} channel={} template={}",
        req.getMessageId(), req.getChannel(), req.getTemplateCode());
    return syncSend(req);
  }

  /**
   * 校验消息请求：通道启用 + 模板存在且 ENABLED + 接收人非空。
   *
   * @param req 待校验的消息发送请求
   * @return 校验失败原因（i18n 文案），null 表示校验通过
   */
  private String validateRequest(MessageItemRequestDTO req) {
    if (!StringUtils.hasText(req.getChannel())) {
      return I18n.message("message.channel.empty");
    }
    if (!StringUtils.hasText(req.getTemplateCode())) {
      return I18n.message("message.template_code.empty");
    }
    if (!StringUtils.hasText(req.getReceiver())) {
      return I18n.message("message.recipient.empty");
    }
    if (!channelRouter.isChannelEnabled(req.getChannel())) {
      return I18n.message("message.send.channel.disabled", new Object[]{req.getChannel()});
    }
    MsgTemplateVO tpl =
        templateService.loadByCodeAndChannel(
            req.getTemplateCode(), req.getChannel(), null,
            AuthContextUtils.getTenantIdOrDefault("1"));
    if (tpl == null) {
      return I18n.message("message.send.template.not.exist", new Object[]{req.getTemplateCode()});
    }
    if (!"ENABLED".equals(tpl.getStatus())) {
      return I18n.message("message.send.template.disabled", new Object[]{tpl.getStatus()});
    }
    return null;
  }

  private void ensureMessageId(MessageItemRequestDTO req) {
    if (!StringUtils.hasText(req.getMessageId())) {
      req.setMessageId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
  }
}
