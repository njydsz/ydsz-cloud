package com.njydsz.pmis.common.event.gateway;

import java.util.List;

import com.njydsz.pmis.common.event.model.OutboxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 降级事件投递网关（装饰器模式）
 *
 * <p>包装一个主 {@link EventPublishGateway}，当主网关投递失败时，
 * 将失败消息的元信息记录到日志（可扩展为写入 fallback 表），
 * 然后返回 false 触发 Outbox 重试机制。
 *
 * <p>使用方式：
 * <pre>{@code
 * @Bean
 * public EventPublishGateway eventPublishGateway(RealGateway realGateway) {
 *     return new FallbackEventPublishGateway(realGateway);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class FallbackEventPublishGateway implements EventPublishGateway {

    private static final Logger log = LoggerFactory.getLogger(FallbackEventPublishGateway.class);

    private final EventPublishGateway delegate;

    /**
     * @param delegate 主投递网关
     */
    public FallbackEventPublishGateway(EventPublishGateway delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean publish(OutboxMessage message) throws Exception {
        try {
            boolean success = delegate.publish(message);
            if (!success) {
                log.warn("Primary gateway returned false for message id={}, type={}. "
                        + "Message will be retried by Outbox processor.",
                        message.getId(), message.getEventType());
            }
            return success;
        } catch (Exception e) {
            log.error("Primary gateway failed for message id={}, type={}. "
                    + "Message will be retried by Outbox processor. Error: {}",
                    message.getId(), message.getEventType(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<Boolean> publishBatch(List<OutboxMessage> messages) throws Exception {
        try {
            List<Boolean> results = delegate.publishBatch(messages);
            for (int i = 0; i < results.size(); i++) {
                if (!results.get(i)) {
                    OutboxMessage msg = messages.get(i);
                    log.warn("Primary gateway batch returned false for message id={}, type={}",
                            msg.getId(), msg.getEventType());
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Primary gateway batch failed. All {} messages will be retried. Error: {}",
                    messages.size(), e.getMessage());
            return messages.stream().map(msg -> false).toList();
        }
    }
}
