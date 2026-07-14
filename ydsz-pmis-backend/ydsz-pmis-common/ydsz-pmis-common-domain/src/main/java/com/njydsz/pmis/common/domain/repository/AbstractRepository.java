package com.njydsz.pmis.common.domain.repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import com.njydsz.pmis.common.domain.entity.AggregateRoot;
import com.njydsz.pmis.common.domain.event.DomainEvent;
import com.njydsz.pmis.common.domain.event.DomainEventPublisher;
import com.njydsz.pmis.common.domain.exception.AggregateNotFoundException;
import com.njydsz.pmis.common.domain.exception.ConcurrencyConflictException;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.domain.query.PageResult;
import com.njydsz.pmis.common.domain.specification.Specification;

/**
 * 仓储抽象基类
 *
 * <p>提供 {@link Repository} 接口的模板方法实现，封装通用的 CRUD 逻辑。
 * 子类通过实现抽象方法对接具体的持久化技术（如 MyBatis-Plus、JPA 等）。
 *
 * <p><b>核心设计：</b>
 * <ul>
 *   <li>模板方法模式：定义 CRUD 流程骨架，子类实现具体持久化细节</li>
 *   <li>乐观锁感知：保存时自动检测版本号变更，抛出 {@link ConcurrencyConflictException}</li>
 *   <li>聚合根未找到感知：查询时自动封装 {@link AggregateNotFoundException}</li>
 *   <li>领域事件自动发布：保存后自动发布聚合根注册的领域事件</li>
 * </ul>
 *
 * <p><b>领域事件发布：</b>
 * 通过 Spring 依赖注入可选地注入 {@link DomainEventPublisher}。
 * 当容器中存在 {@link DomainEventPublisher} Bean 时，保存聚合根后自动发布其注册的领域事件；
 * 当不存在时（如单元测试场景），事件仅被清空而不发布。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * public class OrderRepositoryImpl extends AbstractRepository<Order, Long> {
 *
 *     private final OrderMapper orderMapper;
 *
 *     public OrderRepositoryImpl(OrderMapper orderMapper) {
 *         this.orderMapper = orderMapper;
 *     }
 *
 *     &#64;Override
 *     protected Order doFindById(Long id) {
 *         return orderMapper.selectById(id);
 *     }
 *
 *     &#64;Override
 *     protected Order doSave(Order aggregate) {
 *         if (aggregate.isNew()) {
 *             orderMapper.insert(aggregate);
 *         } else {
 *             int rows = orderMapper.updateById(aggregate);
 *             if (rows == 0) {
 *                 throw new ConcurrencyConflictException("Order", aggregate.getId(), aggregate.getRevision());
 *             }
 *         }
 *         return aggregate;
 *     }
 *
 *     // ... 其他抽象方法实现
 * }
 * }</pre>
 *
 * @param <T>  聚合根类型
 * @param <ID> 主键类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see Repository
 * @see AggregateRoot
 * @see DomainEventPublisher
 */
public abstract class AbstractRepository<T extends AggregateRoot<ID>, ID extends Serializable>
        implements Repository<T, ID> {

    /**
     * 领域事件发布器（可选依赖）
     *
     * <p>由 Spring 容器自动注入。当容器中存在 {@link DomainEventPublisher} Bean 时，
     * 保存聚合根后会自动发布领域事件；不存在时为 null，事件仅被清空。
     */
    @Nullable
    private DomainEventPublisher domainEventPublisher;

    /**
     * 注入领域事件发布器（由 Spring 自动装配）
     *
     * @param domainEventPublisher 领域事件发布器
     */
    @Autowired(required = false)
    public void setDomainEventPublisher(DomainEventPublisher domainEventPublisher) {
        this.domainEventPublisher = domainEventPublisher;
    }

    /**
     * 根据ID查询聚合根（持久化实现）
     *
     * @param id 聚合根ID
     * @return 聚合根实例，不存在时返回 null
     */
    protected abstract T doFindById(ID id);

    /**
     * 保存聚合根（持久化实现）
     *
     * <p>子类应在此方法中实现 INSERT 或 UPDATE 逻辑，
     * 并在乐观锁冲突时抛出 {@link ConcurrencyConflictException}。
     *
     * @param aggregate 聚合根实体
     * @return 保存后的聚合根
     */
    protected abstract T doSave(T aggregate);

    /**
     * 批量保存聚合根（持久化实现）
     *
     * @param aggregates 聚合根集合
     * @return 保存后的聚合根集合
     */
    protected abstract List<T> doSaveAll(Iterable<T> aggregates);

    /**
     * 根据ID删除聚合根（持久化实现）
     *
     * @param id 聚合根ID
     */
    protected abstract void doDelete(ID id);

    /**
     * 根据规约查询所有匹配的聚合根（持久化实现）
     *
     * @param spec 查询规约
     * @return 匹配的聚合根列表
     */
    protected abstract List<T> doFindAll(Specification<T> spec);

    /**
     * 根据规约分页查询聚合根（持久化实现）
     *
     * @param query 分页查询参数
     * @param spec  查询规约
     * @return 分页结果
     */
    protected abstract PageResult<T> doFindPage(PageQuery query, Specification<T> spec);

    /**
     * 根据规约统计匹配的聚合根数量（持久化实现）
     *
     * @param spec 查询规约
     * @return 匹配的记录数
     */
    protected abstract long doCount(Specification<T> spec);

    /**
     * 根据ID判断聚合根是否存在（持久化实现）
     *
     * <p>子类可覆写此方法以提供更高效的存在性检查（如 {@code SELECT EXISTS}）。
     * 默认实现委托至 {@link #doFindById}，会加载完整实体。
     *
     * @param id 聚合根ID
     * @return 存在返回 true
     */
    protected boolean doExistsById(ID id) {
        return doFindById(id) != null;
    }

    @Override
    public Optional<T> findById(ID id) {
        T aggregate = doFindById(id);
        return Optional.ofNullable(aggregate);
    }

    /**
     * 根据ID查询聚合根（不存在时抛出异常）
     *
     * @param id 聚合根ID
     * @return 聚合根实例
     * @throws AggregateNotFoundException 聚合根不存在时抛出
     */
    public T getById(ID id) {
        return findById(id).orElseThrow(() ->
                new AggregateNotFoundException(getAggregateTypeName(), id));
    }

    @Override
    public T save(T aggregate) {
        T saved = doSave(aggregate);
        publishDomainEvents(saved);
        return saved;
    }

    @Override
    public List<T> saveAll(Iterable<T> aggregates) {
        List<T> saved = doSaveAll(aggregates);
        for (T aggregate : saved) {
            publishDomainEvents(aggregate);
        }
        return saved;
    }

    @Override
    public void delete(ID id) {
        doDelete(id);
    }

    @Override
    public void delete(T aggregate) {
        doDelete(aggregate.getId());
    }

    @Override
    public void deleteAll(Iterable<T> aggregates) {
        for (T aggregate : aggregates) {
            doDelete(aggregate.getId());
        }
    }

    @Override
    public List<T> findAll(Specification<T> spec) {
        List<T> all = doFindAll(spec);
        if (all == null) {
            return new ArrayList<>();
        }
        return all;
    }

    @Override
    public boolean exists(Specification<T> spec) {
        return doCount(spec) > 0;
    }

    @Override
    public boolean existsById(ID id) {
        return doExistsById(id);
    }

    @Override
    public PageResult<T> findPage(PageQuery query, Specification<T> spec) {
        return doFindPage(query, spec);
    }

    @Override
    public void batchDelete(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (ID id : ids) {
            doDelete(id);
        }
    }

    @Override
    public long count(Specification<T> spec) {
        return doCount(spec);
    }

    /**
     * 发布聚合根注册的领域事件
     *
     * <p>将聚合根中注册的领域事件通过 {@link DomainEventPublisher} 发布，
     * 发布后自动清空聚合根中的领域事件列表。
     *
     * <p>如果未注入 {@link DomainEventPublisher}（如单元测试场景），
     * 则仅清空事件列表而不发布。
     *
     * @param aggregate 聚合根
     */
    protected void publishDomainEvents(T aggregate) {
        List<DomainEvent> events = aggregate.getDomainEvents();
        if (events == null || events.isEmpty()) {
            return;
        }
        aggregate.clearDomainEvents();
        if (domainEventPublisher != null) {
            for (DomainEvent event : events) {
                domainEventPublisher.publish(event);
            }
        }
    }

    /**
     * 获取聚合根类型名称
     *
     * @return 聚合根类型简单名称
     */
    protected String getAggregateTypeName() {
        return this.getClass().getSimpleName().replace("RepositoryImpl", "");
    }
}
