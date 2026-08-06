package com.remisoft.common.domain.event;

import java.util.ArrayList;
import java.util.List;

/**
 * 领域事件注册能力接口。
 *
 * <p>定义聚合根注册、获取、清理领域事件的标准化契约。
 * 任何需要领域事件支持的类均可实现此接口，不必继承特定基类。
 *
 * <p><b>设计参考：</b>
 * <ul>
 *   <li>Spring Data {@code @DomainEvents} — 声明式事件注册</li>
 *   <li>Axon Framework {@code AbstractAggregateRoot.registerEvent()}</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class Order implements EventRegistry {
 *     private final List<Object> domainEvents = new ArrayList<>();
 *
 *     public void pay() {
 *         // 业务逻辑...
 *         registerEvent(new OrderPaidEvent(this.id));
 *     }
 *
 *     &#64;Override
 *     public void registerEvent(Object event) {
 *         domainEvents.add(event);
 *     }
 *
 *     &#64;Override
 *     public List&lt;Object&gt; pullDomainEvents() {
 *         List&lt;Object&gt; events = new ArrayList&lt;&gt;(domainEvents);
 *         domainEvents.clear();
 *         return events;
 *     }
 *
 *     &#64;Override
 *     public List&lt;Object&gt; getDomainEvents() {
 *         return new ArrayList&lt;&gt;(domainEvents);
 *     }
 *
 *     &#64;Override
 *     public void clearDomainEvents() {
 *         domainEvents.clear();
 *     }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.8.0
 * @see DomainEvent
 * @see DomainEventAspect
 * @see com.remisoft.common.domain.entity.BaseEntity BaseEntity 的默认实现
 */
public interface EventRegistry {

    /**
     * 注册领域事件。
     *
     * <p>事件暂存至内部列表，由 Repository 在持久化后统一分派。
     *
     * @param event 领域事件对象，非 null
     * @throws IllegalArgumentException event 为 null 时
     */
    void registerEvent(Object event);

    /**
     * 获取已暂存的领域事件（不清除）。
     *
     * <p>通常用于查询当前注册的事件，不做清除操作。
     *
     * @return 已注册的事件列表（非 null，可能为空）
     */
    List<Object> getDomainEvents();

    /**
     * 获取并清空已暂存的领域事件。
     *
     * <p>由 Repository 在持久化聚合根之后调用（"读出并清空"语义），
     * 获取后不再保留事件，防止事件被重复分派。
     *
     * @return 已暂存的领域事件列表（非 null，可能为空）
     */
    List<Object> pullDomainEvents();

    /**
     * 清除所有领域事件（不返回）。
     *
     * <p>适用于不需要派发事件的场景（如测试环境、批量导入等）。
     */
    void clearDomainEvents();
}
