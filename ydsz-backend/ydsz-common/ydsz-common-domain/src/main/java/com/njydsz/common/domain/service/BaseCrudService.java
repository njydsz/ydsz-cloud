package com.njydsz.common.domain.service;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.njydsz.common.domain.query.PageResult;

/**
 * 通用 CRUD Service 接口。
 *
 * <p>定义标准的分页查询、按 ID 查询、列表查询、新增、修改、删除、批量操作方法，
 * 由 {@link com.njydsz.common.domain.service.impl.AbstractCrudService} 提供 DDD 仓储默认实现，
 * 由 {@code AbstractMpCrudService}（ydsz-common-jdbc）提供 MyBatis-Plus 默认实现。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}   - 实体类型</li>
 *   <li>{@code DTO} - 数据传输对象（新增/修改入参）</li>
 *   <li>{@code VO}  - 视图对象（出参）</li>
 *   <li>{@code PQ}  - 分页查询参数类型</li>
 *   <li>{@code ID}  - 主键类型</li>
 * </ul>
 *
 * @param <T>   实体类型
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 * @param <ID>  主键类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BaseCrudService<T, DTO, VO, PQ, ID extends Serializable> {

    /**
     * 分页查询。
     *
     * @param query 分页查询参数
     * @return 分页结果（VO）
     */
    PageResult<VO> page(PQ query);

    /**
     * 按 ID 查询。
     *
     * @param id 主键 ID
     * @return 视图对象
     */
    VO getById(ID id);

    /**
     * 列表查询（无条件或按查询参数过滤）。
     *
     * @param query 查询参数，可为 null 表示查询全部
     * @return 视图对象列表
     */
    List<VO> list(PQ query);

    /**
     * 新增。
     *
     * @param dto 数据传输对象
     * @return 主键 ID
     */
    ID save(DTO dto);

    /**
     * 按 ID 修改。
     *
     * @param dto 数据传输对象（需包含 id）
     * @return 是否成功
     */
    boolean updateById(DTO dto);

    /**
     * 按 ID 删除（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(ID id);

    /**
     * 批量新增。
     *
     * @param dtos DTO 集合
     * @return 新增实体的主键 ID 列表
     */
    List<ID> saveBatch(Collection<DTO> dtos);

    /**
     * 批量修改。
     *
     * @param dtos DTO 集合
     * @return 各条修改结果（true 表示成功）
     */
    List<Boolean> updateBatch(Collection<DTO> dtos);

    /**
     * 批量删除。
     *
     * @param ids 主键 ID 集合
     * @return 各条删除结果（true 表示成功）
     */
    List<Boolean> removeBatch(Collection<ID> ids);
}