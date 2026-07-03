package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.engine.ReconcileReport;
import com.njydsz.pmis.project.engine.ReconcileResult;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账服务
 *
 * <p>执行-财务对账：工时漏算成本、工时异常（单日/单周/跨项目）等维度校验。
 *
 * @author ydsz-pmis-team
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
    ReconcileReport reconcileAll(Long initiationId, LocalDate from, LocalDate to);

    /**
     * 单项对账: 工时漏算成本
     *
     * @param initiationId 项目立项 ID
     * @return 对账结果列表
     */
    List<ReconcileResult> checkMissingCost(Long initiationId);

    /**
     * 单项对账: 工时-工时异常 (单日/单周/跨项目)
     *
     * @param initiationId 项目立项 ID
     * @param from         起始日期
     * @param to           结束日期
     * @return 对账结果列表
     */
    List<ReconcileResult> checkTimeEntryAnomaly(Long initiationId, LocalDate from, LocalDate to);
}
