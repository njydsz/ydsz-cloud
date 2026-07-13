package com.njydsz.pmis.workflow.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowAdminRoleDO;

/**
 * 流程管理员角色 Mapper（P1-6）
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Mapper
public interface FlowAdminRoleMapper extends BaseMapper<FlowAdminRoleDO> {

    /**
     * 查询用户在指定租户下的所有有效角色。
     */
    List<FlowAdminRoleDO> selectByUserId(@Param("userId") String userId,
                                          @Param("tenantId") String tenantId);

    /**
     * 查询用户是否拥有指定角色。
     */
    FlowAdminRoleDO selectByUserAndRole(@Param("userId") String userId,
                                         @Param("roleCode") String roleCode,
                                         @Param("tenantId") String tenantId);
}
