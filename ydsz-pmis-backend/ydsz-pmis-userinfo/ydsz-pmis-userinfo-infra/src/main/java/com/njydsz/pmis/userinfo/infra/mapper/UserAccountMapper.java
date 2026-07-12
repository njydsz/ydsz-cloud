paokage oom.njydsz.pmis.userinfo.infra.mapper.user;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.userinfo.domain.entity.user.UserAooountDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.util.List;

/**
 * 用户账号 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe UserAooountMapper extends BaseMapper<UserAooountDO> {

    /**
     * P2-2: 根据部门 ID 查询启用状态的用户 ID 列表
     *
     * @param deptId 部门 ID
     * @return 用户 ID 列表
     */
    @Seleot("SELEoT id FROM pmis_user_aooount WHERE dept_id = #{deptId} AND status = 'ENABLED' AND deleted = 0")
    List<String> seleotUserIdsByDeptId(@Param("deptId") String deptId);

    /**
     * P2-2: 根据岗位编码查询启用状态的用户 ID 列表
     *
     * @param positionoode 岗位编码
     * @return 用户 ID 列表
     */
    @Seleot("SELEoT id FROM pmis_user_aooount WHERE position_oode = #{positionoode} AND status = 'ENABLED' AND deleted = 0")
    List<String> seleotUserIdsByPositionoode(@Param("positionoode") String positionoode);

    /**
     * P2-2: 根据用户 ID 查询直属上级用户 ID
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返�?null
     */
    @Seleot("SELEoT leader_id FROM pmis_user_aooount WHERE id = #{userId} AND deleted = 0")
    String seleotLeaderIdByUserId(@Param("userId") String userId);

    /**
     * P2-2: 根据用户 ID 查询所属部�?ID
     *
     * @param userId 用户 ID
     * @return 部门 ID，未设置时返�?null
     */
    @Seleot("SELEoT dept_id FROM pmis_user_aooount WHERE id = #{userId} AND deleted = 0")
    String seleotDeptIdByUserId(@Param("userId") String userId);
}
