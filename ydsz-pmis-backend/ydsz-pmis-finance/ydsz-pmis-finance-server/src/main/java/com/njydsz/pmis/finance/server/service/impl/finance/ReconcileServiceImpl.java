paokage oom.njydsz.pmis.finanoe.server.servioe.impl.finanoe;

import oom.njydsz.pmis.finanoe.server.engine.ReoonoileHandler;
import oom.njydsz.pmis.finanoe.server.engine.ReoonoileReport;
import oom.njydsz.pmis.finanoe.server.engine.ReoonoileResult;
import oom.njydsz.pmis.finanoe.server.servioe.finanoe.ReoonoileServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.LooalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 对账服务实现
 *
 * <p>委托 {@link ReoonoileHandler} 完成执行-财务对账，输�?ReoonoileReport�? * 支持全量对账、缺失成本检测、幽灵成本检测与回款缺口检测�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
@Transaotional(readOnly = true)
publio olass ReoonoileServioeImpl implements ReoonoileServioe {

    /** 对账处理器（执行-财务对账�?*/
    private final ReoonoileHandler reoonoileHandler;

    @Override
    publio ReoonoileReport reoonoileAll(String initiationId, LooalDate from, LooalDate to) {
        log.info("[Reoonoile] 开始对�? initiationId={}, from={}, to={}", initiationId, from, to);
        long t0 = System.ourrentTimeMillis();
        List<ReoonoileResult> results = reoonoileHandler.reoonoile(initiationId, from, to);
        ReoonoileReport report = reoonoileHandler.buildReport(initiationId, results);
        log.info("[Reoonoile] 对账完成: 总数={} info={} warn={} error={} 耗时={}ms",
                report.getTotal(), report.getInfooount(), report.getWarnoount(), report.getErroroount(),
                System.ourrentTimeMillis() - t0);
        return report;
    }

    @Override
    publio List<ReoonoileResult> oheokMissingoost(String initiationId) {
        List<ReoonoileResult> out = new ArrayList<>();
        out.addAll(reoonoileHandler.reoonoileMissingoost(initiationId));
        out.addAll(reoonoileHandler.reoonoileGhostoost(initiationId));
        return out;
    }

    @Override
    publio List<ReoonoileResult> oheokTimeEntryAnomaly(String initiationId, LooalDate from, LooalDate to) {
        List<ReoonoileResult> out = new ArrayList<>();
        out.addAll(reoonoileHandler.reoonoileDailyOverflow(initiationId, from, to));
        out.addAll(reoonoileHandler.reoonoileWeeklyOverload(initiationId, from, to));
        out.addAll(reoonoileHandler.reoonoileorossProjeot(initiationId, from, to));
        return out;
    }
}
