package com.njydsz.userinfo.server.service.org;

import java.util.List;

import com.njydsz.userinfo.domain.dto.org.DepartmentFormDTO;
import com.njydsz.userinfo.domain.entity.org.DepartmentDO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;

/**
 * 部门服务
 *
 * @author ydsz-team
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
    DepartmentDO getById(String id);

    /**
     * 创建部门
     *
     * @param dto 部门表单
     * @return 新建部门 ID
     */
    String create(DepartmentFormDTO dto);

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
    void delete(String id);
}
