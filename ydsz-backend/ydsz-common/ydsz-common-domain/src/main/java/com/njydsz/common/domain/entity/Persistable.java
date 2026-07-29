package com.njydsz.common.domain.entity;

import java.io.Serializable;

/**
 * 可持久化实体标识接口（Spring Data 风格）。
 *
 * <p>定义所有持久化实体的最小契约：具备主键 ID 并能判断新建/已存在状态。
 * 替代 {@code RootEntity} 中直接定义 getId/setId/isNew 的方式，
 * 使接口职责更单一、组合更灵活。
 *
 * <pre>
 * 使用方式：
 *   BaseIdEntity&lt;Long&gt;  implements Persistable&lt;Long&gt;
 *   自定义实体           implements Persistable&lt;String&gt;
 * </pre>
 *
 * @param <T> 主键 ID 类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface Persistable<T extends Serializable> extends Serializable {

    /**
     * 获取实体主键ID
     *
     * @return 主键ID，未持久化时可能为 null
     */
    T getId();

    /**
     * 设置实体主键ID
     *
     * @param id 主键ID
     */
    void setId(T id);

    /**
     * 判断实体是否为新建状态（尚未持久化）
     *
     * <p>判断逻辑（P2-6 增强：支持 UUID 主键场景）：
     * <ul>
     *   <li>ID 为 null 时，视为新实体</li>
     *   <li>ID 为 Number 类型且值为 0 时，视为新实体（自增主键场景）</li>
     *   <li>ID 为 String/UUID 类型时，如果实体实现了 {@link Auditable}，
     *       则检查 {@code createdAt == null} 判断新建状态</li>
     *   <li>其他情况默认返回 false</li>
     * </ul>
     *
     * @return true 表示新实体（尚未持久化），false 表示已持久化
     */
    default boolean isNew() {
        T id = getId();
        if (id == null) {
            return true;
        }
        if (id instanceof Number) {
            return ((Number) id).longValue() == 0;
        }
        // UUID/String 主键场景：通过 Auditable.createdAt 判断
        if (this instanceof Auditable) {
            return ((Auditable) this).getCreatedAt() == null;
        }
        return false;
    }
}
