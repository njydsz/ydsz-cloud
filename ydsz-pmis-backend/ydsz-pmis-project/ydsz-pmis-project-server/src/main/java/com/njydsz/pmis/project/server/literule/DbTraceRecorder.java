package com.njydsz.pmis.project.server.literule;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.domain.entity.RuleExecutionTraceDO;
import com.njydsz.pmis.literule.infra.mapper.RuleExecutionTraceMapper;
import com.njydsz.pmis.literule.server.spi.TraceRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规则执行轨迹持久化实现（project 模块）
 *
 * <p>将 {@link RuleExecutionTrace} 写入 {@code pmis_rule_execution_trace} 表，
 * 作为 {@link TraceRecorder} SPI 的业务实现，由 {@code AsyncTraceRecorder} 通过
 * {@code setDelegate} 注入作为实际持久化委托。
 *
 * <p>批量写入使用 MyBatis-Plus {@code insertBatchSomeColumn} 等价循环单条插入，
 * 避免引入额外依赖；异步调用方已做攒批，此处单条插入不会阻塞主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DbTraceRecorder implements TraceRecorder {

    private final RuleExecutionTraceMapper ruleExecutionTraceMapper;

    @Override
    public void record(RuleExecutionTrace trace) {
        if (trace == null) {
            return;
        }
        try {
            ruleExecutionTraceMapper.insert(toDO(trace));
        } catch (Exception e) {
            log.warn("[LiteRule-Trace] 单条轨迹写入失败: ruleCode={}, err={}",
                    trace.getRuleCode(), e.getMessage());
        }
    }

    @Override
    public void recordBatch(List<RuleExecutionTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return;
        }
        for (RuleExecutionTrace trace : traces) {
            try {
                ruleExecutionTraceMapper.insert(toDO(trace));
            } catch (Exception e) {
                log.warn("[LiteRule-Trace] 批量轨迹写入失败: ruleCode={}, err={}",
                        trace.getRuleCode(), e.getMessage());
            }
        }
    }

    @Override
    public List<RuleExecutionTrace> getByTraceId(String traceId) {
        List<RuleExecutionTraceDO> list = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .eq(RuleExecutionTraceDO::getTraceId, traceId)
                        .orderByAsc(RuleExecutionTraceDO::getCreatedAt));
        return list.stream().map(this::toTrace).collect(Collectors.toList());
    }

    @Override
    public List<RuleExecutionTrace> getByRuleCode(String ruleCode, int limit) {
        List<RuleExecutionTraceDO> list = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .eq(RuleExecutionTraceDO::getRuleCode, ruleCode)
                        .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        return list.stream().map(this::toTrace).collect(Collectors.toList());
    }

    @Override
    public List<RuleExecutionTrace> getRecentTraces(int limit) {
        List<RuleExecutionTraceDO> list = ruleExecutionTraceMapper.selectList(
                new LambdaQueryWrapper<RuleExecutionTraceDO>()
                        .orderByDesc(RuleExecutionTraceDO::getCreatedAt)
                        .last("LIMIT " + Math.max(1, limit)));
        return list.stream().map(this::toTrace).collect(Collectors.toList());
    }

    /**
     * API 模型 → DO 转换
     */
    private RuleExecutionTraceDO toDO(RuleExecutionTrace trace) {
        RuleExecutionTraceDO d = new RuleExecutionTraceDO();
        d.setTraceId(trace.getTraceId());
        d.setRuleCode(trace.getRuleCode());
        d.setRuleName(trace.getRuleName());
        d.setScenario(trace.getScenario());
        d.setTriggered(trace.isTriggered());
        d.setSeverity(trace.getSeverity());
        d.setConditionResult(trace.getConditionResult());
        d.setElapsedMs(trace.getElapsedMs());
        d.setFactsSnapshot(trace.getFactsSnapshot());
        d.setResultSnapshot(trace.getResultSnapshot());
        d.setErrorMessage(trace.getErrorMessage());
        d.setCreatedAt(trace.getCreatedAt());
        return d;
    }

    /**
     * DO → API 模型转换
     */
    private RuleExecutionTrace toTrace(RuleExecutionTraceDO d) {
        RuleExecutionTrace t = new RuleExecutionTrace();
        t.setTraceId(d.getTraceId());
        t.setRuleCode(d.getRuleCode());
        t.setRuleName(d.getRuleName());
        t.setScenario(d.getScenario());
        t.setTriggered(d.getTriggered() != null && d.getTriggered());
        t.setSeverity(d.getSeverity());
        t.setConditionResult(d.getConditionResult());
        t.setElapsedMs(d.getElapsedMs() != null ? d.getElapsedMs() : 0L);
        t.setFactsSnapshot(d.getFactsSnapshot());
        t.setResultSnapshot(d.getResultSnapshot());
        t.setErrorMessage(d.getErrorMessage());
        t.setCreatedAt(d.getCreatedAt());
        return t;
    }
}
