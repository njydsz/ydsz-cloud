package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentService {

    DepartmentVO getById(String id);
    List<DepartmentVO> list();
    String create(DepartmentSaveDTO dto);
    boolean update(DepartmentSaveDTO dto);
    boolean removeById(String id);
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
}
