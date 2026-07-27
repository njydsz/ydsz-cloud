package com.njydsz.common.domain.service.impl;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.njydsz.common.domain.entity.AggregateRoot;
import com.njydsz.common.domain.exception.AggregateNotFoundException;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.domain.repository.Repository;
import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.common.domain.specification.Specification;

/**
 * 通用 CRUD Service 抽象实现。
 *
 * <p>基于 {@link Repository} 提供标准的分页查询、按 ID 查询、新增、修改、删除、批量操作的默认实现。
 * 子类通过实现少量抽象方法即可快速获得完整 CRUD 能力，并通过生命周期钩子扩展业务逻辑。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}   - 实体类型（聚合根）</li>
 *   <li>{@code DTO} - 数据传输对象</li>
 *   <li>{@code VO}  - 视图对象</li>
 *   <li>{@code PQ}  - 分页查询参数类型</li>
 *   <li>{@code ID}  - 主键类型</li>
 * </ul>
 *
 * <p><b>子类需实现：</b>
 * <ul>
 *   <li>{@link #getRepository()} - 返回仓储实例</li>
 *   <li>{@link #toVO(AggregateRoot)} - 实体转 VO</li>
 *   <li>{@link #toEntity(Object)} - DTO 转实体</li>
 *   <li>{@link #getId(Object)} - 从 DTO 提取主键（更新/删除时）</li>
 * </ul>
 *
 * <p><b>生命周期钩子（AOP 集成点 / 模板方法扩展）：</b>
 * 子类可覆写以下 protected 方法在持久化前后注入业务逻辑：
 * <ul>
 *   <li>{@link #doBeforeSave(Object, AggregateRoot)} / {@link #doAfterSave(AggregateRoot, boolean)} - 新增前后</li>
 *   <li>{@link #doBeforeUpdate(Object, AggregateRoot)} / {@link #doAfterUpdate(AggregateRoot, boolean)} - 更新前后</li>
 *   <li>{@link #doBeforeDelete(ID)} / {@link #doAfterDelete(ID, boolean)} - 删除前后</li>
 *   <li>{@link #doBeforeBatchSave(Iterable)} / {@link #doAfterBatchSave(Iterable)} - 批量新增前后</li>
 *   <li>{@link #doBeforeBatchDelete(Collection)} / {@link #doAfterBatchDelete(Collection)} - 批量删除前后</li>
 * </ul>
 *
 * <p><b>批量操作：</b>
 * 默认基于循环单条持久化实现；子类可覆写 {@link #saveBatch(Collection)}、{@link #updateBatch(Collection)}
 * 或 {@link #removeBatch(Collection)} 以对接数据库批量 SQL（如 MyBatis-Plus 的 {@code insertBatch}）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Service
 * public class UserServiceImpl extends AbstractCrudService<User, UserDTO, UserVO, UserPageQuery>
 *         implements UserService {
 *
 *     private final UserRepository userRepository;
 *
 *     public UserServiceImpl(UserRepository userRepository) {
 *         this.userRepository = userRepository;
 *     }
 *
 *     &#64;Override
 *     protected UserRepository getRepository() {
 *         return userRepository;
 *     }
 *
 *     &#64;Override
 *     protected UserVO toVO(User user) {
 *         return new UserVO(user);
 *     }
 *
 *     &#64;Override
 *     protected User toEntity(UserDTO dto) {
 *         return User.builder()
 *                 .username(dto.getUsername())
 *                 .email(dto.getEmail())
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * @param <T>   实体类型（聚合根）
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 * @param <ID>  主键类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractCrudService<T extends AggregateRoot<ID>, DTO, VO, PQ extends PageQuery, ID extends Serializable>
        implements BaseCrudService<T, DTO, VO, PQ, ID> {

    /**
     * 获取仓储实例。
     *
     * @return 仓储
     */
    protected abstract Repository<T, ID> getRepository();

    /**
     * 实体转视图对象。
     *
     * @param entity 实体
     * @return 视图对象
     */
    protected abstract VO toVO(T entity);

    /**
     * DTO 转实体。
     *
     * <p>子类负责将 DTO 字段映射为实体。新增时 ID 通常为空；更新时 DTO 中应包含 ID。
     *
     * @param dto 数据传输对象
     * @return 实体
     */
    protected abstract T toEntity(DTO dto);

    /**
     * 从 DTO 中提取主键 ID。
     *
     * <p>默认返回 null，子类在支持更新/批量更新时必须实现此方法。
     *
     * @param dto 数据传输对象
     * @return 主键 ID，不存在时返回 null
     */
    protected ID getId(DTO dto) {
        return null;
    }

    /**
     * 构建分页查询条件。
     *
     * <p>子类根据查询参数构建规约条件。返回 null 表示无条件查询（查询所有数据）。
     *
     * @param query 分页查询参数
     * @return 查询条件，返回 null 表示无条件
     */
    protected Specification<T> getPageSpecification(PQ query) {
        return null;
    }

    /**
     * 判断 DTO 是否为新增操作。
     *
     * <p>默认根据 {@link #getId(Object)} 是否为空判断；子类可覆写以适配业务语义。
     *
     * @param dto 数据传输对象
     * @return true 表示新增，false 表示更新
     */
    protected boolean isNew(DTO dto) {
        return getId(dto) == null;
    }

    // ============================== 生命周期钩子（新增） ==============================

    /**
     * 新增前钩子。
     *
     * <p>子类可覆写以执行唯一性校验、默认值填充、业务规则校验等。
     *
     * @param dto    原始 DTO
     * @param entity 转换后的实体
     */
    protected void doBeforeSave(DTO dto, T entity) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 新增后钩子。
     *
     * <p>子类可覆写以执行缓存失效、发布事件、异步通知等。
     *
     * @param saved  保存后的实体
     * @param isNew  是否为新插入（始终为 true，保留参数以便与 update 后钩子语义一致）
     */
    protected void doAfterSave(T saved, boolean isNew) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 更新前钩子。
     *
     * <p>子类可覆写以执行存在性校验、版本校验、业务规则校验等。
     *
     * @param dto    原始 DTO
     * @param entity 转换后的实体
     */
    protected void doBeforeUpdate(DTO dto, T entity) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 更新后钩子。
     *
     * @param saved     保存后的实体
     * @param updated   是否实际更新成功
     */
    protected void doAfterUpdate(T saved, boolean updated) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 删除前钩子。
     *
     * @param id 待删除主键
     */
    protected void doBeforeDelete(ID id) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 删除后钩子。
     *
     * @param id      已删除主键
     * @param removed 是否实际删除成功
     */
    protected void doAfterDelete(ID id, boolean removed) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 批量新增前钩子。
     *
     * @param dtos 原始 DTO 集合
     */
    protected void doBeforeBatchSave(Iterable<DTO> dtos) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 批量新增后钩子。
     *
     * @param saved 保存后的实体集合
     */
    protected void doAfterBatchSave(Iterable<T> saved) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 批量删除前钩子。
     *
     * @param ids 待删除主键集合
     */
    protected void doBeforeBatchDelete(Collection<ID> ids) {
        // 默认空实现，子类按需覆写
    }

    /**
     * 批量删除后钩子。
     *
     * @param ids    已删除主键集合
     * @param result 各 ID 删除结果（true 表示删除成功）
     */
    protected void doAfterBatchDelete(Collection<ID> ids, List<Boolean> result) {
        // 默认空实现，子类按需覆写
    }

    // ============================== 接口实现（查询） ==============================

    @Override
    public PageResult<VO> page(PQ query) {
        Specification<T> spec = getPageSpecification(query);
        PageResult<T> entityPage = getRepository().findPage(query, spec);
        if (entityPage == null) {
            return PageResult.empty(query.getEffectivePageNum(), query.getEffectivePageSize());
        }
        return entityPage.convert(this::toVO);
    }

    @Override
    public VO getById(ID id) {
        T entity = getRepository().findById(id)
                .orElseThrow(() -> new AggregateNotFoundException(getEntityTypeName(), id));
        return toVO(entity);
    }

    @Override
    public List<VO> list(PQ query) {
        Specification<T> spec = getPageSpecification(query);
        List<T> entities = getRepository().findAll(spec);
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ============================== 接口实现（单条） ==============================

    @Override
    public ID save(DTO dto) {
        T entity = toEntity(dto);
        doBeforeSave(dto, entity);
        T saved = getRepository().save(entity);
        doAfterSave(saved, true);
        return saved.getId();
    }

    @Override
    public boolean updateById(DTO dto) {
        T entity = toEntity(dto);
        doBeforeUpdate(dto, entity);
        T saved = getRepository().save(entity);
        doAfterUpdate(saved, true);
        return true;
    }

    @Override
    public boolean removeById(ID id) {
        doBeforeDelete(id);
        getRepository().delete(id);
        doAfterDelete(id, true);
        return true;
    }

    // ============================== 接口实现（批量） ==============================

    @Override
    public List<ID> saveBatch(Collection<DTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        doBeforeBatchSave(dtos);
        List<T> entities = dtos.stream()
                .filter(Objects::nonNull)
                .map(this::toEntity)
                .collect(Collectors.toList());
        for (T entity : entities) {
            doBeforeSave(null, entity);
        }
        List<T> saved = getRepository().saveAll(entities);
        for (T entity : saved) {
            doAfterSave(entity, true);
        }
        doAfterBatchSave(saved);
        return saved.stream().map(AggregateRoot::getId).collect(Collectors.toList());
    }

    @Override
    public List<Boolean> updateBatch(Collection<DTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Boolean> result = dtos.stream()
                .filter(Objects::nonNull)
                .map(this::updateById)
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public List<Boolean> removeBatch(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        doBeforeBatchDelete(ids);
        List<Boolean> result = ids.stream()
                .filter(Objects::nonNull)
                .map(this::removeById)
                .collect(Collectors.toList());
        doAfterBatchDelete(ids, result);
        return result;
    }

    // ============================== 工具方法 ==============================

    /**
     * 获取实体类型名称（用于异常信息）。
     *
     * @return 实体类型简单名称
     */
    protected String getEntityTypeName() {
        return this.getClass().getSimpleName()
                .replace("ServiceImpl", "")
                .replace("Service", "");
    }
}
