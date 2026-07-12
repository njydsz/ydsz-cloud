package com.njydsz.pmis.common.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DDD 聚合根基类 —— 所有领域聚合根的顶层抽象。
 * <p>
 * 对标 remi-comm RootEntity，提供：
 * <ul>
 *   <li>领域事件收集（发布前暂存）</li>
 *   <li>版本号乐观锁</li>
 *   <li>聚合根 ID 约定</li>
 * </ul>
 * </p>
 *
 * @param <ID> 聚合根标识类型
 * @author njydsz
 * @since 1.0.0
 */
public abstract class RootEntity<ID extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 聚合根唯一标识 */
    protected ID id;

    /** 乐观锁版本号 */
    protected Long version;

    /** 创建时间 */
    protected Instant createdAt;

    /** 最后更新时间 */
    protected Instant updatedAt;

    /** 领域事件暂存区（发布前收集） */
    private transient final List<Object> domainEvents = new ArrayList<>();

    /**
     * 注册领域事件，待 Repository.save 后统一发布。
     *
     * @param event 领域事件
     */
    protected void registerEvent(Object event) {
        if (event != null) {
            this.domainEvents.add(event);
        }
    }

    /**
     * 获取并清空领域事件列表。
     *
     * @return 不可变的事件列表副本
     */
    public List<Object> pullDomainEvents() {
        List<Object> snapshot = Collections.unmodifiableList(new ArrayList<>(this.domainEvents));
        this.domainEvents.clear();
        return snapshot;
    }

    /**
     * 检查是否有待发布的领域事件。
     *
     * @return true 如果有待发布事件
     */
    public boolean hasDomainEvents() {
        return !this.domainEvents.isEmpty();
    }

    /**
     * 业务相等性判断 —— 仅比较聚合根 ID。
     * <p>
     * 两个聚合根 ID 相同即视为同一实体，无论其他属性是否一致。
     * </p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RootEntity<?> that = (RootEntity<?>) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : System.identityHashCode(this);
    }

    // --- Getters & Setters ---

    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
