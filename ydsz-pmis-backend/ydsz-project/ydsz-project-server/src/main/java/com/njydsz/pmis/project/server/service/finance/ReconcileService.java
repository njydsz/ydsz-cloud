package com.njydsz.project.server.service.finance;

import java.time.LocalDate;
import java.util.List;

import com.njydsz.project.server.engine.ReconcileReport;
import com.njydsz.project.server.engine.ReconcileResult;

/**
 * 对账服务
 *
 * <p>执行-财务对账：工时漏算成本、工时异常（单日/单周/跨项目）等维度校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ReconcileService {

    /**
     * 全量对账
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账报告
     */
    ReconcileReport reconcileAll(String initiationId, LocalDate from, LocalDate to);

    /**
     * 单项对账: 工时漏算成本
     *
     * @param initiationId 项目立项 ID
     * @return 对账结果列表
     */
    List<ReconcileResult> checkMissingCost(String initiationId);

    /**
     * 单项对账: 工时-工时异常 (单日/单周/跨项目)
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账结果列表
     */
    List<ReconcileResult> checkTimeEntryAnomaly(String initiationId, LocalDate from, LocalDate to);
}
