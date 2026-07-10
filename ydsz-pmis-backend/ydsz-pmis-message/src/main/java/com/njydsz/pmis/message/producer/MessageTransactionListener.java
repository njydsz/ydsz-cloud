package com.njydsz.pmis.message.producer;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.service.template.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 事务消息本地事务监听器（P2-3）。
 *
 * <p>半消息发送成功后,RocketMQ 回调 {@link #executeLocalTransaction} 执行本地事务：
 * <ol>
 *   <li>解析 MessageRequest</li>
 *   <li>校验通道启用 + 模板存在且 ENABLED</li>
 *   <li>校验通过 → COMMIT（半消息投递,消费端可消费）</li>
 *   <li>校验失败 → ROLLBACK（半消息丢弃,消费端不可见）</li>
 * </ol>
 *
 * <p>若 Producer 崩溃未返回 COMMIT/ROLLBACK,RocketMQ 回调 {@link #checkLocalTransaction}
 * 重新校验,决定最终状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener")
@ConditionalOnProperty(prefix = "rocketmq.producer", name = "group")
@RocketMQTransactionListener
public class MessageTransactionListener implements RocketMQLocalTransactionListener {

    private final TemplateService templateService;
    private final ChannelRouter channelRouter;

    @Override
    @SuppressWarnings("rawtypes")
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        MessageRequest req = resolveRequest(message, arg);
        if (req == null) {
            log.warn("[TxListener] executeLocalTransaction: 请求为 null,ROLLBACK");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            String reason = validateRequest(req);
            if (reason != null) {
                log.warn("[TxListener] executeLocalTransaction: 校验失败 ROLLBACK, messageId={} reason={}",
                        req.getMessageId(), reason);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            log.info("[TxListener] executeLocalTransaction: 校验通过 COMMIT, messageId={} channel={} template={}",
                    req.getMessageId(), req.getChannel(), req.getTemplateCode());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("[TxListener] executeLocalTransaction: 异常 ROLLBACK, messageId={} err={}",
                    req.getMessageId(), e.getMessage());
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        MessageRequest req = resolveRequest(message, null);
        if (req == null) {
            log.warn("[TxListener] checkLocalTransaction: 无法解析请求,ROLLBACK");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            String reason = validateRequest(req);
            if (reason != null) {
                log.warn("[TxListener] checkLocalTransaction: 校验失败 ROLLBACK, messageId={} reason={}",
                        req.getMessageId(), reason);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
            log.info("[TxListener] checkLocalTransaction: 校验通过 COMMIT, messageId={}", req.getMessageId());
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("[TxListener] checkLocalTransaction: 异常 UNKNOWN, messageId={} err={}",
                    req.getMessageId(), e.getMessage());
            return RocketMQLocalTransactionState.UNKNOWN;
        }
    }

    /**
     * 从 RocketMQ Message + arg 中解析 MessageRequest。
     *
     * <p>优先从 arg（sendMessageInTransaction 的第三个参数）解析,
     * arg 为 null 时从 message payload 解析。
     */
    private MessageRequest resolveRequest(Message<?> message, Object arg) {
        if (arg instanceof MessageRequest req) {
            return req;
        }
        if (arg != null) {
            try {
                return JsonUtils.parseObject(JsonUtils.toJson(arg), MessageRequest.class);
            } catch (Exception ignored) {
                // fall through to payload parsing
            }
        }
        if (message == null || message.getPayload() == null) {
            return null;
        }
        Object payload = message.getPayload();
        if (payload instanceof String str) {
            return JsonUtils.parseObject(str, MessageRequest.class);
        }
        try {
            return JsonUtils.parseObject(JsonUtils.toJson(payload), MessageRequest.class);
        } catch (Exception e) {
            log.warn("[TxListener] resolveRequest: 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 轻量校验：通道启用 + 模板存在且 ENABLED + 接收人非空。
     *
     * @return null 表示校验通过,非 null 表示失败原因
     */
    private String validateRequest(MessageRequest req) {
        if (!StringUtils.hasText(req.getChannel())) {
            return "通道为空";
        }
        if (!StringUtils.hasText(req.getTemplateCode())) {
            return "模板编码为空";
        }
        if (!StringUtils.hasText(req.getReceiver())) {
            return "接收人为空";
        }
        if (!channelRouter.isChannelEnabled(req.getChannel())) {
            return "通道未启用: " + req.getChannel();
        }
        MsgTemplateDO tpl = templateService.loadByCodeAndChannel(
                req.getTemplateCode(), req.getChannel(), null, TenantContext.getTenantId());
        if (tpl == null) {
            return "模板不存在: " + req.getTemplateCode();
        }
        if (!"ENABLED".equals(tpl.getStatus())) {
            return "模板未启用: " + tpl.getStatus();
        }
        return null;
    }
}
