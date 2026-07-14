package com.njydsz.pmis.common.domain.repository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.pmis.common.domain.entity.AggregateRoot;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.domain.query.PageResult;
import com.njydsz.pmis.common.domain.specification.Specification;

/**
 * 仓储接口
 *
 * <p>在领域驱动设计（DDD）中，仓储（Repository）是聚合根的持久化抽象�?
 * 它对外提供类似集合的接口，屏蔽底层持久化技术细节�?
 *
 * <p><b>核心语义�?/b>
 * <ul>
 *   <li>仓储是聚合根的持久化抽象，每个聚合根对应一个仓�?/li>
 *   <li>仓储接口定义在领域层，实现在基础设施�?/li>
 *   <li>通过 Specification 模式实现灵活的条件查�?/li>
 * </ul>
 *
 * <p><b>使用示例�?/b>
 * <pre>{@code
 * public interface OrderRepository extends Repository<Order, Long> {
 *     // 可继承本接口提供的基础方法，也可定义业务特定方�?
 * }
 *
 * // 使用
 * Order order = repository.findById(orderId).orElse(null);
 * repository.save(order);
 * repository.delete(orderId);
 * }</pre>
 *
 * @param <T>  聚合根类型，必须实现 {@link AggregateRoot} 接口
 * @param <ID> 主键类型，必须可序列�?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface Repository<T extends AggregateRoot<ID>, ID extends Serializable> {

    /**
     * 根据ID查询聚合�?
     *
     * @param id 聚合根ID
     * @return 聚合根实例，不存在时返回�?
     */
    Optional<T> findById(ID id);

    /**
     * 保存聚合根（新增或更新）
     *
     * <p>根据聚合根的ID是否存在，自动判断是新增还是更新操作�?
     *
     * @param aggregate 聚合根实�?
     * @return 保存后的聚合�?
     */
    T save(T aggregate);

    /**
     * 批量保存聚合�?
     *
     * @param aggregates 聚合根列�?
     * @return 保存后的聚合根列�?
     */
    List<T> saveAll(Iterable<T> aggregates);

    /**
     * 根据ID删除聚合�?
     *
     * @param id 聚合根ID
     */
    void delete(ID id);

    /**
     * 删除指定聚合�?
     *
     * @param aggregate 聚合根实�?
     */
    void delete(T aggregate);

    /**
     * 批量删除聚合�?
     *
     * @param aggregates 聚合根列�?
     */
    void deleteAll(Iterable<T> aggregates);

    /**
     * 根据规约查询所有匹配的聚合�?
     *
     * @param spec 查询规约
     * @return 匹配的聚合根列表
     */
    List<T> findAll(Specification<T> spec);

    /**
     * 根据规约查询是否存在匹配的聚合根
     *
     * @param spec 查询规约
     * @return 存在返回true
     */
    boolean exists(Specification<T> spec);

    /**
     * 判断指定ID的聚合根是否存在
     *
     * @param id 聚合根ID
     * @return 存在返回true
     */
    boolean existsById(ID id);

    /**
     * 根据规约分页查询聚合�?
     *
     * @param query 分页查询参数
     * @param spec  查询规约
     * @return 分页结果
     */
    PageResult<T> findPage(PageQuery query, Specification<T> spec);

    /**
     * 批量保存聚合�?
     *
     * @param entities 聚合根集�?
     */
    void batchSave(Collection<T> entities);

    /**
     * 批量删除聚合根（根据ID�?
     *
     * @param ids 聚合根ID集合
     */
    void batchDelete(Collection<ID> ids);

    /**
     * 根据规约统计匹配的聚合根数量
     *
     * @param spec 查询规约
     * @return 匹配的记录数
     */
    long count(Specification<T> spec);
}
