package com.njydsz.common.domain.service;

import com.njydsz.common.domain.query.PageResult;

/**
 * 通用 CRUD Service 接口。
 *
 * <p>定义标准的分页查询、按 ID 查询、新增、修改、删除方法，
 * 由 {@link com.njydsz.common.domain.service.impl.AbstractCrudService} 提供默认实现。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}  - 实体类型</li>
 *   <li>{@code DTO} - 数据传输对象（新增/修改入参）</li>
 *   <li>{@code VO}  - 视图对象（出参）</li>
 *   <li>{@code PQ}  - 分页查询参数类型</li>
 * </ul>
 *
 * @param <T>  实体类型
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BaseCrudService<T, DTO, VO, PQ> {

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
    VO getById(String id);

    /**
     * 新增。
     *
     * @param dto 数据传输对象
     * @return 主键 ID
     */
    String save(DTO dto);

    /**
     * 按 ID 修改。
     *
     * @param dto 数据传输对象
     * @return 是否成功
     */
    boolean updateById(DTO dto);

    /**
     * 按 ID 删除（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
