package com.njydsz.pmis.project.server.service.impl.finance;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.pmis.project.server.engine.ReconcileHandler;
import com.njydsz.pmis.project.server.engine.ReconcileReport;
import com.njydsz.pmis.project.server.engine.ReconcileResult;
import com.njydsz.pmis.project.server.service.finance.ReconcileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 对账服务实现
 *
 * <p>委托 {@link ReconcileHandler} 完成执行-财务对账，输出 ReconcileReport。
 * 支持全量对账、缺失成本检测、幽灵成本检测与回款缺口检测。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReconcileServiceImpl implements ReconcileService {

    /** 对账处理器（执行-财务对账） */
    private final ReconcileHandler reconcileHandler;

    @Override
    public ReconcileReport reconcileAll(String initiationId, LocalDate from, LocalDate to) {
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
    public List<ReconcileResult> checkMissingCost(String initiationId) {
        List<ReconcileResult> out = new ArrayList<>();
        out.addAll(reconcileHandler.reconcileMissingCost(initiationId));
        out.addAll(reconcileHandler.reconcileGhostCost(initiationId));
        return out;
    }

    @Override
    public List<ReconcileResult> checkTimeEntryAnomaly(String initiationId, LocalDate from, LocalDate to) {
        List<ReconcileResult> out = new ArrayList<>();
        out.addAll(reconcileHandler.reconcileDailyOverflow(initiationId, from, to));
        out.addAll(reconcileHandler.reconcileWeeklyOverload(initiationId, from, to));
        out.addAll(reconcileHandler.reconcileCrossProject(initiationId, from, to));
        return out;
    }
}
