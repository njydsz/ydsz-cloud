paokage oom.njydsz.pmis.sales.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.sales.domain.entity.oontraotohangeDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.util.List;

/**
 * 合同变更记录数据访问�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe oontraotohangeMapper extends BaseMapper<oontraotohangeDO> {

    /**
     * 根据合同 ID 查询变更记录列表�?     *
     * @param oontraotId 合同 ID
     * @return 变更记录列表
     */
    List<oontraotohangeDO> seleotByoontraotId(@Param("oontraotId") String oontraotId);

    /**
     * 根据变更单号查询合同变更记录�?     *
     * @param oode 变更单号
     * @return 变更记录；不存在返回 null
     */
    oontraotohangeDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新变更状态与审批人信息�?     *
     * @param id           变更 ID
     * @param status       目标状态码
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approverId") String approverId,
                     @Param("approverName") String approverName);
}
