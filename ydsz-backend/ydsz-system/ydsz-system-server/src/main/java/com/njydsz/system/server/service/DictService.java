package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.query.DictPageQuery;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典类型 Service。
 *
 * <p>提供字典类型 CRUD、全量列表查询等能力。
 *
 * @author ydsz-team
 */
public interface DictService {

    /**
     * 分页查询字典类型。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResult<DictTypeVO> page(DictPageQuery query);

    /**
     * 按 ID 查询字典类型。
     *
     * @param id 主键 ID
     * @return 字典类型 VO
     */
    DictTypeVO getById(String id);

    /**
     * 创建字典类型。
     *
     * @param dto 字典类型 DTO
     * @return 主键 ID
     */
    String save(DictTypeDTO dto);

    /**
     * 更新字典类型。
     *
     * @param dto 字典类型 DTO
     * @return 是否成功
     */
    boolean updateById(DictTypeDTO dto);

    /**
     * 删除字典类型。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);

    /**
     * 查询全部字典类型（不区分状态）。
     *
     * @return 字典类型列表
     */
    List<DictTypeVO> listAll();
}
