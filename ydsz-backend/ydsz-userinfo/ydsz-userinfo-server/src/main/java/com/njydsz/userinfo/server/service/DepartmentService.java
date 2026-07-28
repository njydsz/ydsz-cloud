package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.userinfo.domain.dto.post.DepartmentPostDTO;
import com.njydsz.userinfo.domain.dto.put.DepartmentPutDTO;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Service 接口
 *
 * <p>封装部门的完整业务逻辑：CRUD、树形结构查询、跨服务富化、审批人展开查询。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Department 部门实体
 */
public interface DepartmentService {

    /**
     * 根据 ID 查询部门详情。
     *
     * @param id 部门 ID
     * @return 部门 VO
     */
    DepartmentVO getById(String id);

    /**
     * 查询全部部门列表（扁平结构）。
     *
     * @return 部门 VO 列表
     */
    List<DepartmentVO> list();

    /**
     * 查询部门树形结构。
     *
     * @return 部门树形结构列表
     */
    List<DepartmentTreeVO> tree();

    /**
     * 创建部门。
     *
     * @param dto 部门创建 DTO
     * @return 新部门 ID
     */
    String create(DepartmentPostDTO dto);

    /**
     * 更新部门。
     *
     * @param dto 部门更新 DTO（含 ID）
     * @return true=成功
     */
    boolean update(DepartmentPutDTO dto);

    /**
     * 删除部门（逻辑删除）。
     *
     * @param id 部门 ID
     * @return true=成功
     */
    boolean removeById(String id);

    /**
     * 按部门 ID 查询部门负责人 ID。
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID
     */
    String getDeptLeaderByDeptId(String deptId);

    /**
     * 按部门编码查询部门负责人 ID。
     *
     * @param deptCode 部门编码
     * @return 部门负责人用户 ID
     */
    String getDeptLeaderByDeptCode(String deptCode);

    /**
     * 批量查询部门 ID → 部门名映射。
     *
     * @param deptIds 部门 ID 集合
     * @return deptId → deptName 映射
     */
    Map<String, String> batchNamesByIds(Collection<String> deptIds);
}
