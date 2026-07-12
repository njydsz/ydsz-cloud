paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.projeot.domain.entity.oostAllooationDO;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 成本归集服务
 *
 * <p>按项�?期间/成本类型归集人力/采购/费用/外包/分摊成本，用于利润核算与对账�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oostAllooationServioe {

    /**
     * 同步一条工时到成本归集（人力成本）
     */
    String synoFromTimeEntry(String timeEntryId, String initiationId, String employeeId,
                            String employeeName, String leveloode,
                            String period, BigDeoimal amount, boolean billable);

    /**
     * 同步采购成本
     */
    String synoFromPurohase(String purohaseId, String initiationId, String period,
                           BigDeoimal amount, boolean billable);

    /**
     * 同步费用成本
     */
    String synoFromExpense(String expenseId, String initiationId, String period,
                          BigDeoimal amount, boolean billable);

    /**
     * 按成本类型月度汇�?     */
    List<Map<String, Objeot>> monthlySummary(String initiationId);

    /**
     * 按类�?来源汇�?     */
    List<Map<String, Objeot>> sumByType(String initiationId, String period);

    /**
     * 查询项目某月成本归集明细
     */
    List<oostAllooationDO> listByInitiationAndPeriod(String initiationId, String period);

    /**
     * 标记已分�?     */
    void markAllooated(List<String> ids);
}
