package com.njydsz.pmis.common.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 跨域事件基类
 *
 * <p>所有跨域 RocketMQ 事件均继承此类，携带事件元信息（ID/时间戳/来源/类型），
 * 便于消费方做幂等去重、链路追踪和事件溯源。
 *
 * <h2>使用方式</h2>
 * <pre>{@code
 * // 生产方
 * ContractSignedEvent event = new ContractSignedEvent();
 * event.setContractId("C001");
 * event.setContractAmount(new BigDecimal("100000"));
 * rocketMQTemplate.convertAndSend(CrossDomainEventTopics.SALES_CONTRACT_SIGNED, event);
 *
 * // 消费方
 * @RocketMQMessageListener(topic = CrossDomainEventTopics.SALES_CONTRACT_SIGNED,
 *     consumerGroup = CrossDomainEventTopics.CG_FINANCE_CONTRACT_SIGNED)
 * public class ContractSignedListener implements RocketMQListener<ContractSignedEvent> { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Data
public abstract class CrossDomainEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件唯一 ID（用于幂等去重） */
    private String eventId = UUID.randomUUID().toString();

    /** 事件发生时间 */
    private LocalDateTime eventTime = LocalDateTime.now();

    /** 事件来源服务名 */
    private String source;

    /** 事件类型 */
    private String eventType;

    /** 租户 ID */
    private String tenantId;

    /** 链路追踪 ID */
    private String traceId;
}
