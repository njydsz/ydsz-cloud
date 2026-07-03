package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;

import java.util.Map;

/**
 * 流程 SLA（Service Level Agreement）超时自动策略服务
 *
 * <p>P1-6: 后端超时自动策略（PASS/REJECT/NOTIFY/ESCALATE）
 * <p>对标钉钉/飞书审批的 SLA 自动化能力：
 * <ul>
 *   <li>任务到 dueAt 时由 cronjob 扫描触发</li>
 *   <li>按节点 slaConfig 配置自动执行：REMIND/ESCALATE/AUTO_PASS/AUTO_REJECT</li>
 *   <li>每次扫描会增加 reminder_count，达到 maxReminders 时切换为最终动作</li>
 *   <li>所有异常都被 try-catch 吞掉，cronjob 主循环不会因单条任务失败而中断</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowSlaService {

    /**
     * 扫描所有到点任务并执行 SLA 策略（cronjob 每 60s 调用一次）
     *
     * @return 实际处理的任务数
     */
    int scanAndProcess();

    /**
     * 处理单条任务的 SLA（外部可主动触发）
     *
     * @param task 当前任务（必须已 setAssigneeId + dueAt）
     * @return true=已处理或无需处理；false=处理异常
     */
    boolean processOverdue(FlowTaskDO task);

    /**
     * 应用 SLA 配置到任务（创建任务时调用，解析 node.slaConfig 设置 dueAt）
     *
     * @param task 待设置 dueAt 的任务
     * @param node 当前节点（slaConfig 字段从 ext 中读取）
     */
    void applySlaConfig(FlowTaskDO task, FlowNodeDO node);

    /**
     * 解析 SLA 配置（同时支持 slaConfig JSON 和 ext.slaConfig 嵌套）
     *
     * @param slaConfigJson slaConfig 字段原始 JSON
     * @return 配置 Map（缺省时返回空 Map）
     */
    Map<String, Object> parseSlaConfig(String slaConfigJson);
}
