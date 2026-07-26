package com.njydsz.common.domain.service.impl;

import com.njydsz.common.domain.entity.AggregateRoot;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.domain.repository.Repository;
import com.njydsz.common.domain.specification.Specification;
import com.njydsz.common.domain.service.BaseCrudService;

/**
 * 通用 CRUD Service 抽象实现。
 *
 * <p>基于 {@link Repository} 提供标准的分页查询、按 ID 查询、新增、修改、删除默认实现。
 * 子类通过实现抽象方法对接具体的实体转换和查询条件构建逻辑。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}  - 实体类型（聚合根）</li>
 *   <li>{@code DTO} - 数据传输对象</li>
 *   <li>{@code VO}  - 视图对象</li>
 *   <li>{@code PQ}  - 分页查询参数类型</li>
 * </ul>
 *
 * <p><b>子类需实现：</b>
 * <ul>
 *   <li>{@link #getRepository()} - 返回仓储实例</li>
 *   <li>{@link #toVO(AggregateRoot)} - 实体转 VO</li>
 *   <li>{@link #toEntity(Object)} - DTO 转实体</li>
 *   <li>{@link #getPageSpecification(PageQuery)} - 构建查询条件（可选，返回 null 表示无条件查询）</li>
 * </ul>
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
 *
 *     &#64;Override
 *     protected Specification<User> getPageSpecification(UserPageQuery query) {
 *         return Specification.where(user -> {
 *             if (query.getUsername() != null
 *                     && !user.getUsername().contains(query.getUsername())) {
 *                 return false;
 *             }
 *             return true;
 *         });
 *     }
 * }
 * }</pre>
 *
 * @param <T>  实体类型（聚合根）
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractCrudService<T extends AggregateRoot<String>, DTO, VO, PQ extends PageQuery>
        implements BaseCrudService<T, DTO, VO, PQ> {

    /**
     * 获取仓储实例。
     *
     * @return 仓储
     */
    protected abstract Repository<T, String> getRepository();

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
     * @param dto 数据传输对象
     * @return 实体
     */
    protected abstract T toEntity(DTO dto);

    /**
     * 构建分页查询条件。
     *
     * <p>子类根据查询参数构建规约条件。
     * 返回 null 表示无条件查询（查询所有数据）。
     *
     * @param query 分页查询参数
     * @return 查询条件，返回 null 表示无条件
     */
    protected Specification<T> getPageSpecification(PQ query) {
        return null;
    }

    /**
     * 获取实体的 ID 值。
     *
     * <p>默认从 {@link AggregateRoot#getId()} 获取。
     * 子类可覆写以适配非标准 ID 取值方式。
     *
     * @param entity 实体
     * @return ID 字符串值
     */
    protected String getEntityId(T entity) {
        return entity.getId();
    }

    // ============================== 接口实现 ==============================

    @Override
    public PageResult<VO> page(PQ query) {
        Specification<T> spec = getPageSpecification(query);
        PageResult<T> entityPage = getRepository().findPage(query, spec);
        return entityPage.convert(this::toVO);
    }

    @Override
    public VO getById(String id) {
        T entity = getRepository().findById(id)
                .orElseThrow(() -> new com.njydsz.common.domain.exception.AggregateNotFoundException(
                        getEntityTypeName(), id));
        return toVO(entity);
    }

    @Override
    public String save(DTO dto) {
        T entity = toEntity(dto);
        T saved = getRepository().save(entity);
        return getEntityId(saved);
    }

    @Override
    public boolean updateById(DTO dto) {
        T entity = toEntity(dto);
        getRepository().save(entity);
        return true;
    }

    @Override
    public boolean removeById(String id) {
        getRepository().delete(id);
        return true;
    }

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
