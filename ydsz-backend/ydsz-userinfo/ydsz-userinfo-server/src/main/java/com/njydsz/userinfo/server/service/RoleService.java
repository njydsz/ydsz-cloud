package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.RolePageQueryDTO;
import com.njydsz.userinfo.domain.dto.RoleSaveDTO;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * 角色 Service 接口
 *
 * <p>封装角色的完整业务逻辑：CRUD、权限分配、跨服务名称富化。
 * 继承 {@link BaseCrudService} 获取标准 CRUD 能力，新增权限分配与名称批量查询能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>角色 CRUD（继承自 {@link BaseCrudService}）</li>
 *   <li>角色权限分配（{@code assignPermissions}，覆盖式：清空旧关联 + 批量插入新关联）</li>
 *   <li>角色权限查询（{@code getRolePermissionIds}）</li>
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/assignPermissions}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Role 角色实体
 * @see com.njydsz.userinfo.web.controller.RoleController 角色 Controller
 */
public interface RoleService extends BaseCrudService<Role, RoleSaveDTO, RoleVO, RolePageQueryDTO, String> {

    /**
     * 查询全部角色列表（无分页，按 sortOrder 升序）。
     *
     * <p>适用于下拉框、单选/多选场景；不返回分页信息。
     *
     * @return 角色 VO 列表（按 {@code sortOrder} 升序）
     */
    List<RoleVO> list();

    /**
     * 为角色分配权限（全量覆盖模式）。
     *
     * <p>事务内：① 清空旧角色-权限关联；② 批量插入新关联。任一失败回滚。
     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表（可空/空列表表示清空所有权限）
     * @return true=成功
     */
    boolean assignPermissions(String roleId, List<String> permissionIds);

    /**
     * 查询角色的权限 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表（无权限时返回空列表）
     */
    List<String> getRolePermissionIds(String roleId);

    /**
     * 批量查询角色 ID → 角色名映射（供 NameAssembler 跨服务富化 roleName 字段）。
     *
     * <p>实现：单条 SQL {@code SELECT id, role_name FROM ydsz_role WHERE id IN (...)}，
     * 一次往返拿到全部结果。已逻辑删除的角色不会出现在结果中。
     *
     * @param roleIds 角色 ID 集合（允许 null / 空，返回空 Map）
     * @return roleId → roleName 映射；未命中的 roleId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> roleIds);
}
