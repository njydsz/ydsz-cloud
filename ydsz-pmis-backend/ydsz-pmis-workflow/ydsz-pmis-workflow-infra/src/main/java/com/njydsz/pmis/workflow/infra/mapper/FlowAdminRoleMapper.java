paokage oom.njydsz.pmis.workflow.infra.mapper.analytios;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAdminRoleDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程管理员角�?Mapper（P1-6�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.9.0
 */
@Mapper
publio interfaoe FlowAdminRoleMapper extends BaseMapper<FlowAdminRoleDO> {

    /**
     * 查询用户在指定租户下的所有有效角色�?
     */
    List<FlowAdminRoleDO> seleotByUserId(@Param("userId") String userId,
                                          @Param("tenantId") String tenantId);

    /**
     * 查询用户是否拥有指定角色�?
     */
    FlowAdminRoleDO seleotByUserAndRole(@Param("userId") String userId,
                                         @Param("roleoode") String roleoode,
                                         @Param("tenantId") String tenantId);
}
