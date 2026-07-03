package com.njydsz.pmis.userinfo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户账号 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountDO> {

    /**
     * P2-2: 根据部门 ID 查询启用状态的用户 ID 列表
     *
     * @param deptId 部门 ID
     * @return 用户 ID 列表
     */
    @Select("SELECT id FROM pmis_user_account WHERE dept_id = #{deptId} AND status = 'ENABLED' AND deleted = 0")
    List<Long> selectUserIdsByDeptId(@Param("deptId") Long deptId);

    /**
     * P2-2: 根据岗位编码查询启用状态的用户 ID 列表
     *
     * @param positionCode 岗位编码
     * @return 用户 ID 列表
     */
    @Select("SELECT id FROM pmis_user_account WHERE position_code = #{positionCode} AND status = 'ENABLED' AND deleted = 0")
    List<Long> selectUserIdsByPositionCode(@Param("positionCode") String positionCode);

    /**
     * P2-2: 根据用户 ID 查询直属上级用户 ID
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返回 null
     */
    @Select("SELECT leader_id FROM pmis_user_account WHERE id = #{userId} AND deleted = 0")
    Long selectLeaderIdByUserId(@Param("userId") Long userId);

    /**
     * P2-2: 根据用户 ID 查询所属部门 ID
     *
     * @param userId 用户 ID
     * @return 部门 ID，未设置时返回 null
     */
    @Select("SELECT dept_id FROM pmis_user_account WHERE id = #{userId} AND deleted = 0")
    Long selectDeptIdByUserId(@Param("userId") Long userId);
}
