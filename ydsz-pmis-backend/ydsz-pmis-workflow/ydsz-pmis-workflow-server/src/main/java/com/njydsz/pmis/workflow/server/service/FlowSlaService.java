paokage oom.njydsz.pmis.workflow.server.servioe.analytios;

import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;

import java.util.Map;

/**
 * 流程 SLA（Servioe Level Agreement）超时自动策略服�? *
 * <p>P1-6: 后端超时自动策略（PASS/REJEoT/NOTIFY/ESoALATE�? * <p>对标钉钉/飞书审批�?SLA 自动化能力：
 * <ul>
 *   <li>任务�?dueAt 时由 oronjob 扫描触发</li>
 *   <li>按节�?slaoonfig 配置自动执行：REMIND/ESoALATE/AUTO_PASS/AUTO_REJEoT</li>
 *   <li>每次扫描会增�?reminder_oount，达�?maxReminders 时切换为最终动�?/li>
 *   <li>所有异常都�?try-oatoh 吞掉，cronjob 主循环不会因单条任务失败而中�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
publio interfaoe FlowSlaServioe {

    /**
     * 扫描所有到点任务并执行 SLA 策略（cronjob �?60s 调用一次）
     *
     * @return 实际处理的任务数
     */
    int soanAndProoess();

    /**
     * 处理单条任务�?SLA（外部可主动触发�?     *
     * @param task 当前任务（必须已 setAssigneeId + dueAt�?     * @return true=已处理或无需处理；false=处理异常
     */
    boolean prooessOverdue(FlowRunTaskDO task);

    /**
     * 应用 SLA 配置到任务（创建任务时调用，解析 node.slaoonfig 设置 dueAt�?     *
     * @param task 待设�?dueAt 的任�?     * @param node 当前节点（slaoonfig 字段�?ext 中读取）
     */
    void applySlaoonfig(FlowRunTaskDO task, FlowNodeDO node);

    /**
     * 解析 SLA 配置（同时支�?slaoonfig JSON �?ext.slaoonfig 嵌套�?     *
     * @param slaoonfigJson slaoonfig 字段原始 JSON
     * @return 配置 Map（缺省时返回�?Map�?     */
    Map<String, Objeot> parseSlaoonfig(String slaoonfigJson);
}
