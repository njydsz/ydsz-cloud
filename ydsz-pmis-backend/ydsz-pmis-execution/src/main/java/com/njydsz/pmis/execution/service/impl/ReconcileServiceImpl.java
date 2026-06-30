package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.engine.ReconcileHandler;
import com.njydsz.pmis.execution.engine.ReconcileReport;
import com.njydsz.pmis.execution.engine.ReconcileResult;
import com.njydsz.pmis.execution.service.ReconcileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileServiceImpl implements ReconcileService {

    private final ReconcileHandler reconcileHandler;

    @Override
    public ReconcileReport reconcileAll(Long initiationId, LocalDate from, LocalDate to) {
        log.info("[Reconcile] 开始对账: initiationId={}, from={}, to={}", initiationId, from, to);
        long t0 = System.currentTimeMillis();
        List<ReconcileResult> results = reconcileHandler.reconcile(initiationId, from, to);
        ReconcileReport report = reconcileHandler.buildReport(initiationId, results);
        log.info("[Reconcile] 对账完成: 总数={} info={} warn={} error={} 耗时={}ms",
                report.getTotal(), report.getInfoCount(), report.getWarnCount(), report.getErrorCount(),
                System.currentTimeMillis() - t0);
        return report;
    }

    @Override
    public List<ReconcileResult> checkMissingCost(Long initiationId) {
        List<ReconcileResult> out = new ArrayList<>();
        out.addAll(reconcileHandler.reconcileMissingCost(initiationId));
        out.addAll(reconcileHandler.reconcileGhostCost(initiationId));
        return out;
    }

    @Override
    public List<ReconcileResult> checkTimeEntryAnomaly(Long initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        out.addAll(reconcileHandler.reconcileDailyOverflow(initiationId, from, to));
        out.addAll(reconcileHandler.reconcileWeeklyOverload(initiationId, from, to));
        out.addAll(reconcileHandler.reconcileCrossProject(initiationId, from, to));
        return out;
    }
}
