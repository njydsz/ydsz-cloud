package com.njydsz.pmis.iam.service;

import com.njydsz.pmis.iam.dto.DepartmentFormDTO;
import com.njydsz.pmis.iam.entity.DepartmentDO;
import com.njydsz.pmis.iam.vo.DepartmentTreeVO;

import java.util.List;

/**
 * 部门服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface DepartmentService {

    /**
     * 获取部门树
     *
     * @return 部门树
     */
    List<DepartmentTreeVO> tree();

    /**
     * 列出所有启用的部门（扁平）
     *
     * @return 启用部门列表
     */
    List<DepartmentDO> listAllEnabled();

    /**
     * 根据 ID 获取
     *
     * @param id 部门 ID
     * @return 部门实体，不存在时返回 null
     */
    DepartmentDO getById(Long id);

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 新建部门 ID
     */
    Long create(DepartmentFormDTO dto);

    /**
     * 更新部门
     *
     * @param dto 部门表单
     */
    void update(DepartmentFormDTO dto);

    /**
     * 删除部门（逻辑删除，含子部门校验）
     *
     * @param id 部门 ID
     */
    void delete(Long id);
}
