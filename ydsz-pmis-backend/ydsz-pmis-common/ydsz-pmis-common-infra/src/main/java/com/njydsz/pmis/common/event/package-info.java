/**
 * 跨域事件总线 — RocketMQ Topic 注册表 + 事件 DTO
 *
 * <p>DDD 拆分后，三大业务域（sales / finance / project）之间的异步通信通过 RocketMQ 事件总线完成。
 * 本包集中管理所有跨域 Topic 定义、事件 DTO 和消费者组命名。
 *
 * <h2>架构概览</h2>
 * <pre>
 * ┌─────────┐  ContractSignedEvent   ┌──────────┐
 * │  Sales  │ ─────────────────────→ │ Finance  │ (创建回款计划)
 * │         │  OpportunityWonEvent   │          │
 * │         │ ─────────────────────→ │ Project  │ (创建立项)
 * └─────────┘                        └──────────┘
 * ┌──────────┐  PaymentReceivedEvent  ┌─────────┐
 * │ Finance  │ ─────────────────────→ │ Project │ (更新预算)
 * │          │  InvoiceIssuedEvent   │         │
 * │          │ ─────────────────────→ │  Sales  │ (更新开票进度)
 * └──────────┘                        └─────────┘
 * ┌─────────┐  InitiationCreatedEvent ┌──────────┐
 * │ Project │ ─────────────────────→ │ Finance  │ (初始化预算)
 * │         │  BudgetExceededEvent   │          │
 * │         │ ─────────────────────→ │ Finance  │ (冻结付款)
 * │         │  ClosureApprovedEvent  │          │
 * │         │ ─────────────────────→ │ Sales    │ (释放保证金)
 * └─────────┘                        └──────────┘
 * </pre>
 *
 * <h2>Topic 注册表</h2>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#SALES_CONTRACT_SIGNED} — 合同签订</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#SALES_OPPORTUNITY_WON} — 商机赢单</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#FINANCE_PAYMENT_RECEIVED} — 回款到账</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#FINANCE_INVOICE_ISSUED} — 发票开具</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#PROJECT_INITIATION_CREATED} — 立项创建</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#PROJECT_BUDGET_EXCEEDED} — 预算超限</li>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEventTopics#PROJECT_CLOSURE_APPROVED} — 收尾审批</li>
 * </ul>
 *
 * <h2>事件 DTO</h2>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.event.CrossDomainEvent} — 基类（eventId/eventTime/source/traceId）</li>
 *   <li>{@link com.njydsz.pmis.common.event.ContractSignedEvent} — 合同签订事件</li>
 *   <li>{@link com.njydsz.pmis.common.event.PaymentReceivedEvent} — 回款到账事件</li>
 *   <li>{@link com.njydsz.pmis.common.event.InitiationCreatedEvent} — 立项创建事件</li>
 * </ul>
 *
 * <h2>使用规范</h2>
 * <ul>
 *   <li>生产方：在 Service 层事务提交后发布事件（{@code @TransactionalEventListener(phase = AFTER_COMMIT)}）</li>
 *   <li>消费方：实现 {@code RocketMQListener<T>}，通过 {@code @RocketMQMessageListener} 注解声明 Topic + ConsumerGroup</li>
 *   <li>幂等：消费方通过 {@code eventId} 做幂等去重（Redis SETNX 或 DB 唯一约束）</li>
 *   <li>重试：RocketMQ 消费失败自动重试 3 次，超过后进入死信队列</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.common.event;
