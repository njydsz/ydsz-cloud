package com.njydsz.common.core.contract;

import java.io.Serializable;

/**
 * 事件对象标记接口。
 *
 * <p>用于标识领域事件/集成事件对象。零方法开销，仅做编译期类型约束和文档化作用。
 * 领域事件应继承 {@link com.njydsz.common.core.event.DomainEvent} 获得完整的事件元数据支持。</p>
 *
 * @author ydsz-team
 * @since 1.5.0
 * @see com.njydsz.common.core.event.DomainEvent
 * @see DTO
 * @see VO
 */
public interface Event extends Serializable {
}
