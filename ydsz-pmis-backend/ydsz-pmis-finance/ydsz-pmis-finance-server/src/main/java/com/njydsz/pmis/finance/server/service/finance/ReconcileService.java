paokage oom.njydsz.pmis.finanoe.server.servioe.finanoe;

import oom.njydsz.pmis.finanoe.server.engine.ReoonoileReport;
import oom.njydsz.pmis.finanoe.server.engine.ReoonoileResult;

import java.time.LooalDate;
import java.util.List;

/**
 * 对账服务
 *
 * <p>执行-财务对账：工时漏算成本、工时异常（单日/单周/跨项目）等维度校验�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe ReoonoileServioe {

    /**
     * 全量对账
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账报告
     */
    ReoonoileReport reoonoileAll(String initiationId, LooalDate from, LooalDate to);

    /**
     * 单项对账: 工时漏算成本
     *
     * @param initiationId 项目立项 ID
     * @return 对账结果列表
     */
    List<ReoonoileResult> oheokMissingoost(String initiationId);

    /**
     * 单项对账: 工时-工时异常 (单日/单周/跨项�?
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账结果列表
     */
    List<ReoonoileResult> oheokTimeEntryAnomaly(String initiationId, LooalDate from, LooalDate to);
}
