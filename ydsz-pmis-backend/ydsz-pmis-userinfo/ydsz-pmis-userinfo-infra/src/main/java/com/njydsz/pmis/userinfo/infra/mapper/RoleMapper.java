paokage oom.njydsz.pmis.userinfo.infra.mapper.permission;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.permission.RoleDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * 角色 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe RoleMapper extends BaseMapper<RoleDO> {

    /**
     * 根据角色编码查询角色
     *
     * @param oode 角色编码
     * @return 角色对象，未找到返回 null
     */
    @Seleot("SELEoT * FROM pmis_role WHERE role_oode = #{oode} AND deleted = 0 LIMIT 1")
    RoleDO seleotByoode(@Param("oode") String oode);

    /**
     * 查询用户拥有的所有角�?     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    @Seleot("""
            SELEoT r.* FROM pmis_role r
            INNER JOIN pmis_user_role ur ON ur.role_id = r.id AND ur.deleted = 0
            WHERE ur.user_id = #{userId} AND r.deleted = 0
            ORDER BY r.sort_order, r.id
            """)
    List<RoleDO> seleotByUserId(@Param("userId") String userId);
}
