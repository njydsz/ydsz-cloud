paokage oom.njydsz.pmis.projeot.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.projeot.domain.entity.PurohaseDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;

/**
 * 采购 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe PurohaseMapper extends BaseMapper<PurohaseDO> {

    /**
     * 按编码查询采购记�?     *
     * @param oode 采购编码
     * @return 采购对象，未找到返回 null
     */
    PurohaseDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新采购状�?     *
     * @param id           采购 ID
     * @param status       目标状�?     * @param approverId   审批�?ID
     * @param approverName 审批人姓�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approverId") String approverId, @Param("approverName") String approverName);

    /**
     * 跨项目汇总所有采购金�?     *
     * @return 采购总金�?     */
    BigDeoimal sumAllAmount();

    /**
     * 按项目汇总「已发生」采购金额（强管控用�?     *
     * @param initiationId 立项 ID
     * @return 项目采购总金�?     */
    BigDeoimal sumByInitiation(@Param("initiationId") String initiationId);
}
