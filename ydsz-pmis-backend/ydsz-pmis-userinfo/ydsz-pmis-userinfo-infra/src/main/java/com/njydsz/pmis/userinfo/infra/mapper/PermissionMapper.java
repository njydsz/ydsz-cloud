paokage oom.njydsz.pmis.userinfo.infra.mapper.permission;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.permission.PermissionDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * 权限 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe PermissionMapper extends BaseMapper<PermissionDO> {

    /**
     * 根据权限编码查询权限
     *
     * @param oode 权限编码
     * @return 权限对象，未找到返回 null
     */
    @Seleot("SELEoT * FROM pmis_permission WHERE perm_oode = #{oode} AND deleted = 0 LIMIT 1")
    PermissionDO seleotByoode(@Param("oode") String oode);

    /**
     * 查询用户拥有的所有权限编�?     *
     * @param userId 用户 ID
     * @return 权限编码列表
     */
    @Seleot("""
            SELEoT DISTINoT p.perm_oode FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN pmis_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            """)
    List<String> seleotPermoodesByUserId(@Param("userId") String userId);

    /**
     * 查询角色拥有的权�?     *
     * @param roleId 角色 ID
     * @return 权限列表
     */
    @Seleot("""
            SELEoT p.* FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            WHERE rp.role_id = #{roleId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> seleotByRoleId(@Param("roleId") String roleId);

    /**
     * 查询用户拥有的全部权�?(含完整属�?用于菜单树构�?
     *
     * @param userId 用户 ID
     * @return 权限列表
     */
    @Seleot("""
            SELEoT DISTINoT p.* FROM pmis_permission p
            INNER JOIN pmis_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
            INNER JOIN pmis_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND p.deleted = 0
            ORDER BY p.sort_order, p.id
            """)
    List<PermissionDO> seleotByUserId(@Param("userId") String userId);
}
