package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.engine.ReconcileReport;
import com.njydsz.pmis.execution.engine.ReconcileResult;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账服务
 */
public interface ReconcileService {

    /**
     * 全量对账
     */
    ReconcileReport reconcileAll(Long initiationId, LocalDate from, LocalDate to);

    /**
     * 单项对账: 工时漏算成本
     */
    List<ReconcileResult> checkMissingCost(Long initiationId);

    /**
     * 单项对账: 工时-工时异常 (单日/单周/跨项目)
     */
    List<ReconcileResult> checkTimeEntryAnomaly(Long initiationId, LocalDate from, LocalDate to);
}
