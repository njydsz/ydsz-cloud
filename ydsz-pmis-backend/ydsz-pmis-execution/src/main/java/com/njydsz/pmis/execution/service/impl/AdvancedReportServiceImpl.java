package com.njydsz.pmis.execution.service.impl;

import com.njydsz.pmis.execution.entity.EvmMeasureDO;
import com.njydsz.pmis.execution.entity.RateCardDO;
import com.njydsz.pmis.execution.entity.RateInternalDO;
import com.njydsz.pmis.execution.entity.RiskDO;
import com.njydsz.pmis.execution.mapper.EvmMeasureMapper;
import com.njydsz.pmis.execution.mapper.RateCardMapper;
import com.njydsz.pmis.execution.mapper.RateInternalMapper;
import com.njydsz.pmis.execution.mapper.RiskMapper;
import com.njydsz.pmis.execution.service.AdvancedReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 高级报表 Service 实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedReportServiceImpl implements AdvancedReportService {

    private final EvmMeasureMapper evmMapper;
    private final RateCardMapper rateCardMapper;
    private final RateInternalMapper rateInternalMapper;
    private final RiskMapper riskMapper;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Override
    public List<Map<String, Object>> evmReport(Long initiationId) {
        if (initiationId == null) {
            return new ArrayList<>();
        }
        List<EvmMeasureDO> list = evmMapper.selectByInitiation(initiationId);
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (EvmMeasureDO m : list) {
            Map<String, Object> row = new HashMap<>();
            row.put("period", m.getPeriod());
            row.put("wbsTaskId", m.getWbsTaskId());
            row.put("pv", m.getPv());
            row.put("ev", m.getEv());
            row.put("ac", m.getAc());
            row.put("bac", m.getBac());
            row.put("cpi", m.getCpi());
            row.put("spi", m.getSpi());
            row.put("cv", m.getCv());
            row.put("sv", m.getSv());
            row.put("vac", m.getVac());
            row.put("alertLevel", m.getAlertLevel());
            row.put("alertReason", m.getAlertReason());
            out.add(row);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> utilizationRank(int top) {
        if (top <= 0) {
            top = 10;
        }
        // 此处按职级 × 内部成本率作为"人效近似"维度
        List<RateInternalDO> rates = safeAll(rateInternalMapper, r -> r.selectAll());
        List<Map<String, Object>> out = new ArrayList<>();
        for (RateInternalDO r : rates) {
            Map<String, Object> row = new HashMap<>();
            row.put("levelCode", r.getLevelCode());
            row.put("costAmount", r.getCostAmount());
            row.put("department", r.getDepartmentName());
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> m) ->
                toBigDecimal(m.get("costAmount"))).reversed());
        if (out.size() > top) {
            return out.subList(0, top);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> benchCostReport() {
        // 跨模块：当前阶段返回空，由后续集成 user Feign 后填充
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> dualRateProfitCompare(String period) {
        List<RateCardDO> cards = safeAll(rateCardMapper, r -> r.selectAll());
        List<RateInternalDO> internals = safeAll(rateInternalMapper, r -> r.selectAll());
        Map<String, RateCardDO> cardMap = cards.stream()
                .collect(Collectors.toMap(RateCardDO::getLevelCode, c -> c, (a, b) -> a));
        Map<String, RateInternalDO> internalMap = internals.stream()
                .collect(Collectors.toMap(RateInternalDO::getLevelCode, c -> c, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (String level : cardMap.keySet()) {
            RateCardDO card = cardMap.get(level);
            RateInternalDO internal = internalMap.get(level);
            BigDecimal external = card == null ? ZERO : nz(card.getRateAmount());
            BigDecimal internalCost = internal == null ? ZERO : nz(internal.getCostAmount());
            BigDecimal diff = external.subtract(internalCost);
            BigDecimal margin = external.signum() == 0
                    ? ZERO
                    : diff.divide(external, 4, RoundingMode.HALF_UP);
            Map<String, Object> row = new HashMap<>();
            row.put("levelCode", level);
            row.put("externalRate", external);
            row.put("internalCost", internalCost);
            row.put("diff", diff);
            row.put("margin", margin);
            out.add(row);
        }
        if (StringUtils.hasText(period)) {
            out.sort(Comparator.comparing((Map<String, Object> m) ->
                    toBigDecimal(m.get("diff"))).reversed());
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> resourceGantt(Long initiationId) {
        // 跨模块：当前返回空，由后续集成 user Feign 后填充
        return new ArrayList<>();
    }

    @Override
    public List<Map<String, Object>> riskDashboard() {
        List<RiskDO> risks = new ArrayList<>();
        try {
            risks = riskMapper.selectAll();
        } catch (Exception e) {
            log.warn("[AdvancedReport] 风险数据查询失败: {}", e.getMessage());
        }
        Map<String, Integer> byLevel = new HashMap<>();
        Map<Long, Integer> byInitiation = new HashMap<>();
        for (RiskDO r : risks) {
            String level = r.getRiskLevel() == null ? "UNKNOWN" : r.getRiskLevel();
            byLevel.merge(level, 1, Integer::sum);
            byInitiation.merge(r.getInitiationId(), 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byLevel.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "BY_LEVEL");
            row.put("key", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        for (Map.Entry<Long, Integer> e : byInitiation.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("type", "BY_INITIATION");
            row.put("initiationId", e.getKey());
            row.put("count", e.getValue());
            out.add(row);
        }
        return out;
    }

    // ----------------- 私有 -----------------

    private BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return ZERO;
        }
    }

    private <T, R> List<R> safeAll(T mapper, java.util.function.Function<T, List<R>> fn) {
        try {
            return fn.apply(mapper);
        } catch (Exception e) {
            log.warn("[AdvancedReport] 聚合查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
