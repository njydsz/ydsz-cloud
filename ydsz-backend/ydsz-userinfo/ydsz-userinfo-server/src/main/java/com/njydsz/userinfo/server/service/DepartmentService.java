package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.post.DepartmentPostDTO;
import com.njydsz.userinfo.domain.dto.put.DepartmentPutDTO;
import com.njydsz.userinfo.domain.entity.Department;
import com.njydsz.userinfo.domain.query.DepartmentPageQuery;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Service 接口
 *
 * <p>封装部门的完整业务逻辑：CRUD、树形结构查询、跨服务富化、审批人展开查询。
 * 部门是组织架构的核心节点，支持无限级树形结构（{@code parentId="0"} = 根部门）。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>部门 CRUD（继承自 {@link BaseCrudService}）</li>
 *   <li>部门全量列表查询（{@code list}，按 {@code sortOrder} 升序）</li>
 *   <li>部门树形结构查询（{@code tree}，递归构建父子关系）</li>
 *   <li>部门负责人查询（{@code getDeptLeaderByDeptId} / {@code getDeptLeaderByDeptCode}，
 *       供工作流 {@code dept:xxx} 审批人展开调用）</li>
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）</li>
 * </ul>
 *
 * <p><b>数据权限关联：</b>{@link com.njydsz.userinfo.domain.entity.Role#getDataScope}
 * 通过部门树实现「本部门及子部门」「仅本部门」数据隔离范围。
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Department 部门实体
 * @see com.njydsz.userinfo.web.controller.DepartmentController 部门 Controller
 */
public interface DepartmentService extends BaseCrudService<Department, DepartmentSaveDTO, DepartmentVO, DepartmentPageQuery, String> {

    /**
     * 查询全部部门列表。
     *
     * <p>返回扁平列表（不嵌套 children），按 {@code sortOrder} 升序。
     *
     * @return 部门 VO 列表（按 {@code sortOrder} 升序）
     */
    List<DepartmentVO> list();

    /**
     * 查询部门树形结构。
     *
     * <p>递归构建父子关系：根节点 {@code parentId = "0"} → 子部门 → 孙部门。
     * 适用于前端组织架构树渲染。
     *
     * @return 部门树形结构列表（根节点列表，每个根节点含 {@code children} 嵌套）
     */
    List<DepartmentTreeVO> tree();

    /**
     * 按部门 ID 查询部门负责人 ID（供 Feign 跨服务调用，支持工作流 {@code dept:数字} 审批人展开）。
     *
     * @param deptId 部门 ID
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    String getDeptLeaderByDeptId(String deptId);

    /**
     * 按部门编码查询部门负责人 ID（供 Feign 跨服务调用，支持工作流 {@code dept:非数字} 审批人展开）。
     *
     * @param deptCode 部门编码
     * @return 部门负责人用户 ID，未设置时返回 null
     */
    String getDeptLeaderByDeptCode(String deptCode);

    /**
     * 批量查询部门 ID → 部门名映射（供 NameAssembler 跨服务富化 deptName 字段）。
     *
     * <p>实现：单条 SQL {@code SELECT id, dept_name FROM ydsz_department WHERE id IN (...)}，
     * 一次往返拿到全部结果。已逻辑删除的部门不会出现在结果中。
     *
     * @param deptIds 部门 ID 集合（允许 null / 空，返回空 Map）
     * @return deptId → deptName 映射；未命中的 deptId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> deptIds);
}
