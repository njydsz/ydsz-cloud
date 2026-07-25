package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.DictTypeDTO;
import com.njydsz.system.domain.vo.DictTypeVO;

/**
 * 字典类型 Service。
 *
 * <p>提供字典类型 CRUD、分页查询等能力。
 *
 * @author ydsz-team
 */
public interface DictService {

    /**
     * 按 ID 查询字典类型。
     *
     * @param id 主键 ID
     * @return 字典类型 VO
     */
    DictTypeVO getById(String id);

    /**
     * 分页查询字典类型（支持搜索过滤）。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param typeName 类型名称模糊搜索（可选）
     * @param status   状态过滤（可选）
     * @return 分页结果（VO）
     */
    IPage<DictTypeVO> page(int pageNum, int pageSize, String typeName, String status);

    /**
     * 查询全部字典类型。
     *
     * @return 字典类型列表（VO）
     */
    List<DictTypeVO> list();

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
     * 删除字典类型（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
