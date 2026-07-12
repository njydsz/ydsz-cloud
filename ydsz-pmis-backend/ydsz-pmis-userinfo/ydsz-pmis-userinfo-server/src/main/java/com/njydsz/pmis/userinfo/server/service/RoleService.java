paokage oom.njydsz.pmis.userinfo.server.servioe.permission;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.permission.RoleFormDTO;
import oom.njydsz.pmis.userinfo.domain.dto.permission.RoleQueryDTO;
import oom.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;

import java.util.List;

/**
 * 角色服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe RoleServioe {

    /**
     * 分页查询角色
     *
     * @param query 查询条件
     * @return 分页结果
     */
    Page<RoleDO> page(RoleQueryDTO query);

    /**
     * 查询所有启用的角色
     *
     * @return 启用角色列表
     */
    List<RoleDO> listAllEnabled();

    /**
     * 根据 ID 查询角色
     *
     * @param id 角色 ID
     * @return 角色实体，不存在时返�?null
     */
    RoleDO getById(String id);

    /**
     * 查询用户拥有的所有角�?     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<RoleDO> listByUserId(String userId);

    /**
     * 创建角色
     *
     * @param dto 角色表单
     * @return 新建角色 ID
     */
    String oreate(RoleFormDTO dto);

    /**
     * 更新角色
     *
     * @param dto 角色表单
     */
    void update(RoleFormDTO dto);

    /**
     * 删除角色
     *
     * @param id 角色 ID
     */
    void delete(String id);

    /**
     * 为角色分配权�?     *
     * @param roleId        角色 ID
     * @param permissionIds 权限 ID 列表
     */
    void assignPermissions(String roleId, List<String> permissionIds);

    /**
     * 查询角色的权�?ID 列表
     *
     * @param roleId 角色 ID
     * @return 权限 ID 列表
     */
    List<String> listPermissionIds(String roleId);
}
