package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.entity.DepartmentDO;
import com.njydsz.userinfo.domain.query.DepartmentPageQuery;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentService extends BaseCrudService<DepartmentDO, DepartmentSaveDTO, DepartmentVO, DepartmentPageQuery, String> {

    /**
     * 查询全部部门列表。
     *
     * @return 部门视图对象列表
     */
    List<DepartmentVO> list();

    /**
     * 查询部门树形结构。
     *
     * @return 部门树形结构列表
     */
    List<DepartmentTreeVO> tree();

    /**
     * 按部门 ID 查询部门负责人 ID（供 Feign 跨服务调用，支持工作流 dept:数字 审批人展开）。
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    String getDeptLeaderByDeptId(String deptId);

    /**
     * 按部门编码查询部门负责人 ID（供 Feign 跨服务调用，支持工作流 dept:非数字 审批人展开）。
     *
     * @param deptCode 部门编码
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    String getDeptLeaderByDeptCode(String deptCode);

    /**
     * 批量查询部门 ID → 部门名映射（供 NameAssembler 跨服务富化 deptName 字段）。
     *
     * @param deptIds 部门 ID 集合（允许 null / 空，返回空 Map）
     * @return deptId → deptName 映射；未命中的 deptId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> deptIds);
}