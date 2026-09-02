package com.njydsz.message.server.consumer;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.idempotent.IdempotentStrategy;
import com.njydsz.common.queue.constant.YdszMessageTopics;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * RocketMQ 死信队列消费者。
 *
 * <p>监听 {@link YdszMessageTopics#DLQ_MESSAGE},将重试耗尽的消息落库 status=DEAD, 不抛出异常避免 DLQ 循环重投。
 *
 * <p>幂等去重:Redis SET NX EX 防止 rebalance 重投导致重复处理; 落库时优先按 msgId 更新已有记录状态为 DEAD,未匹配才 insert,避免重复 msgId
 * 记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
@ConditionalOnProperty(
    prefix = "rocketmq.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
@RocketMQMessageListener(
    topic = YdszMessageTopics.DLQ_MESSAGE,
    consumerGroup = YdszMessageTopics.GROUP_DLQ_MESSAGE,
    selectorExpression = "*",
    maxReconsumeTimes = 1)
public class MessageDlqConsumer implements RocketMQListener<MessageExt> {
  /** 内容日志截断长度 */
  private static final int BODY_LOG_MAX_LENGTH = 500;


  /** DLQ 幂等锁前缀 */
  private static final String DLQ_IDEMPOTENT_PREFIX = "ydsz:msg:dlq:idempotent:";

  /** DLQ 幂等锁 TTL(1 小时,DLQ maxReconsumeTimes=1 重投概率低) */
  private static final long DLQ_IDEMPOTENT_TTL_SECONDS = 3600L;

  private final MsgLogRepository msgLogRepository;
  private final MessageMetrics messageMetrics;
  private final IdempotentStrategy idempotentStrategy;

  @Override
  public void onMessage(MessageExt messageExt) {
    if (messageExt == null) {
      log.warn("[MessageDlqConsumer] 收到 null 消息,跳过");
      return;
    }
    String msgId = messageExt.getMsgId();
    int reconsumeTimes = messageExt.getReconsumeTimes();
    String body = new String(messageExt.getBody() == null ? new byte[0] : messageExt.getBody());
    String originTopic = messageExt.getTopic();

    // Redis SET NX EX 幂等去重:防止 rebalance 重投导致重复处理
    String idempotentKey = DLQ_IDEMPOTENT_PREFIX + msgId;
    String dlqToken = idempotentStrategy.acquire(idempotentKey, DLQ_IDEMPOTENT_TTL_SECONDS * 1000L);
    if (dlqToken == null) {
      log.info("[MessageDlqConsumer] 重复死信已跳过: msgId={}", msgId);
      return;
    }

    // P1-3: 死信处理进入追踪上下文（无原始 traceId 时自动生成）
    try (MessageTracer.MessageTraceScope scope = MessageTracer.enter(null)) {
      MessageRequest request = null;
      try {
        request = YdszJson.fromJson(body, MessageRequest.class);
      } catch (Exception e) {
        log.error("[MessageDlqConsumer] 死信消息体解析失败: msgId={} err={}", msgId, e.getMessage(), e);
      }

      try {
        String errorMessage =
            String.format(
                "DLQ: msgId=%s, originTopic=%s, reconsumeTimes=%d",
                msgId, originTopic, reconsumeTimes);

        // 优先按 request.msgId 更新已有记录状态为 DEAD
        String bizMsgId = request != null ? request.getMessageId() : null;
        if (bizMsgId != null && !bizMsgId.isBlank()) {
          MsgLogVO existingVO = findByMsgId(bizMsgId);
          if (existingVO != null) {
            existingVO.setStatus(MessageStatusEnum.DEAD.name());
            existingVO.setErrorMessage(errorMessage);
            existingVO.setReconsumeTimes(reconsumeTimes);
            msgLogRepository.update(existingVO);
            log.info("[MessageDlqConsumer] 已更新现有记录为 DEAD: msgId={}", bizMsgId);
            messageMetrics.recordDead(request != null ? request.getChannel() : "UNKNOWN");
            return;
          }
        }

        // 未匹配到已有记录,insert 新的 DEAD 记录
        MsgLogVO logVO = new MsgLogVO();
        if (request != null) {
          logVO.setChannel(request.getChannel());
          logVO.setBizType(request.getBizType());
          logVO.setBizId(request.getBizId());
          logVO.setReceiver(request.getReceiver());
          logVO.setTemplateCode(request.getTemplateCode());
          logVO.setContent(request.getContent());
          logVO.setMsgId(bizMsgId);
        } else {
          logVO.setChannel("UNKNOWN");
          logVO.setReceiver("UNKNOWN");
          logVO.setContent(body.length() > BODY_LOG_MAX_LENGTH ? body.substring(0, BODY_LOG_MAX_LENGTH) + "..." : body);
        }
        logVO.setStatus(MessageStatusEnum.DEAD.name());
        logVO.setErrorMessage(errorMessage);
        logVO.setTopic(originTopic);
        logVO.setReconsumeTimes(reconsumeTimes);
        logVO.setTenantId(TenantContextHolder.getTenantId());
        msgLogRepository.save(logVO);
        messageMetrics.recordDead(logVO.getChannel());
      } catch (Exception e) {
        log.error("[MessageDlqConsumer] 死信落库失败: msgId={} err={}", msgId, e.getMessage(), e);
      }

      log.error(
          "[MessageDlqConsumer] 死信已落库: msgId={} originTopic={} reconsumeTimes={} bizType={} bizId={} receiver={}",
          msgId,
          originTopic,
          reconsumeTimes,
          request == null ? null : request.getBizType(),
          request == null ? null : request.getBizId(),
          request == null ? null : request.getReceiver());
    }
  }

  /**
   * 按 msgId 精确查找消息日志 VO。
   *
   * @param msgId 消息 ID（业务 ID）
   * @return 消息日志 VO，未找到返回 null
   */
  private MsgLogVO findByMsgId(String msgId) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setMsgId(msgId);
    query.setPageNum(1);
    query.setPageSize(1);
    Optional<MsgLogVO> result = msgLogRepository.findOne(query);
    return result.orElse(null);
  }
}
