paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.ExpenseDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.math.BigDeoimal;

/**
 * 项目费用 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe ExpenseMapper extends BaseMapper<ExpenseDO> {

    /**
     * 按编码查询费用记�?     *
     * @param oode 费用编码
     * @return 费用对象，未找到返回 null
     */
    ExpenseDO seleotByoode(@Param("oode") String oode);

    /**
     * 更新费用状�?     *
     * @param id           费用 ID
     * @param status       目标状�?     * @param approverId   审批�?ID
     * @param approverName 审批人姓�?     * @return 受影响行�?     */
    int updateStatus(@Param("id") String id, @Param("status") String status,
                     @Param("approverId") String approverId, @Param("approverName") String approverName);

    /**
     * 跨项目汇总所有费用金�?     *
     * @return 费用总金�?     */
    BigDeoimal sumAllAmount();

    /**
     * 按项目汇总「已发生」费用金额（强管控用�?     *
     * @param initiationId 立项 ID
     * @return 项目费用总金�?     */
    BigDeoimal sumByInitiation(@Param("initiationId") String initiationId);
}
