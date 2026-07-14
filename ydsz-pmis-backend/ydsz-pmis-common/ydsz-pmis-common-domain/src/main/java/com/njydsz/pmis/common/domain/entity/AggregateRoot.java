package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;
import java.util.List;

import com.njydsz.pmis.common.domain.event.DomainEvent;

/**
 * 聚合根标记接口
 *
 * <p>在领域驱动设计（DDD）中，聚合根（Aggregate Root）是聚合的入口和唯一标识。
 * 外部对象只能通过聚合根来访问聚合内部的实体和值对象，从而保证聚合内的一致性边界。
 *
 * <p><b>核心语义：</b>
 * <ul>
 *   <li>聚合根是聚合的一致性边界守护者，所有对聚合内部状态的变更必须通过聚合根进行</li>
 *   <li>聚合根拥有全局唯一标识，是聚合内唯一可被外部直接引用的实体</li>
 *   <li>聚合根负责维护聚合内部的不变量（Invariant），确保业务规则的一致性</li>
 *   <li>聚合根可以发布领域事件，通知外部关于聚合状态变更的信息</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Order extends BaseEntity<Long> implements AggregateRoot<Long> {
 *
 *     private String orderNo;
 *     private List<OrderItem> items;
 *     private final List<DomainEvent> domainEvents = new ArrayList<>();
 *
 *     public void addItem(Product product, int quantity) {
 *         // 通过聚合根维护业务规约
 *         if (this.items.size() >= 100) {
 *             throw new BusinessException("订单项不能超。00）;
 *         }
 *         this.items.add(new OrderItem(product, quantity));
 *         registerEvent(new OrderItemAddedEvent(this.getId(), product.getId(), quantity));
 *     }
 *
 *     &#64;Override
 *     public List<DomainEvent> getDomainEvents() {
 *         return domainEvents;
 *     }
 *
 *     &#64;Override
 *     public void clearDomainEvents() {
 *         domainEvents.clear();
 *     }
 * }
 * }</pre>
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>尽量保持聚合小巧，只包含必要的一致性边界</li>
 *   <li>聚合之间通过标识（ID）引用，而非直接对象引用</li>
 *   <li>一次事务只修改一个聚合根</li>
 * </ul>
 *
 * @param <T> 主键 ID 类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see Persistable
 */
public interface AggregateRoot<T extends Serializable> extends Persistable<T> {

    /**
     * 注册领域事件
     *
     * @param event 领域事件
     */
    default void registerEvent(DomainEvent event) {
        getDomainEvents().add(event);
    }

    /**
     * 获取已注册的领域事件
     *
     * @return 领域事件列表
     */
    List<DomainEvent> getDomainEvents();

    /**
     * 清空已注册的领域事件
     */
    void clearDomainEvents();
}
