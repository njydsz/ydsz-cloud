package com.njydsz.pmis.common.event.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.common.event.model.OutboxMessage;
import com.njydsz.pmis.common.event.model.OutboxStatus;
import com.njydsz.pmis.common.event.repository.OutboxRepository;

/**
 * Outbox 写入服务
 *
 * <p>核心入口：业务代码在数据库事务中调用 {@link #appendToOutbox}，
 * 将领域事件写入 Outbox 表。事务提交后，后台轮询器异步投递。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     private final OutboxService outboxService;
 *
 *     @Transactional
 *     public void createOrder(OrderCreateDTO dto) {
 *         Order order = orderMapper.insert(dto);
 *
 *         // 同一事务写入 Outbox
 *         outboxService.appendToOutbox(
 *             "Order", order.getId(), "OrderCreated",
 *             toJson(order)
 *         );
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;
    private final int defaultMaxRetries;

    /**
     * @param outboxRepository Outbox 仓储
     * @param defaultMaxRetries 默认最大重试次数
     */
    public OutboxService(OutboxRepository outboxRepository, int defaultMaxRetries) {
        this.outboxRepository = outboxRepository;
        this.defaultMaxRetries = defaultMaxRetries;
    }

    /**
     * 追加事件到 Outbox（在当前数据库事务中执行）
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根 ID
     * @param eventType     事件类型
     * @param payload       事件负载（JSON）
     */
    @Transactional
    public void appendToOutbox(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        appendToOutbox(aggregateType, aggregateId, eventType, payload, null);
    }

    /**
     * 追加事件到 Outbox（带扩展头）
     *
     * @param aggregateType 聚合根类型
     * @param aggregateId   聚合根 ID
     * @param eventType     事件类型
     * @param payload       事件负载（JSON）
     * @param headers       扩展头
     */
    @Transactional
    public void appendToOutbox(String aggregateType, String aggregateId,
                               String eventType, String payload,
                               Map<String, String> headers) {
        Instant now = Instant.now();
        OutboxMessage message = OutboxMessage.builder()
                .id(UUID.randomUUID().toString())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .payload(payload)
                .headers(headers)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .maxRetries(defaultMaxRetries)
                .nextRetryAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        outboxRepository.save(message);
        log.debug("Outbox message appended: id={}, type={}, aggregate={}/{}",
                message.getId(), eventType, aggregateType, aggregateId);
    }
}
