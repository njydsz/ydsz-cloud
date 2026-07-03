package com.njydsz.pmis.literule.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 规则执行轨迹记录
 *
 * <p>对应持久化表 {@code pmis_rule_execution_trace}（见 V043）。
 * 由 {@code TraceRecorder} 在每条规则评估后异步写入。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public class RuleExecutionTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 追踪 ID（同一批次评估共享） */
    private String traceId;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名 */
    private String ruleName;

    /** 业务场景 */
    private String scenario;

    /** 是否触发 */
    private boolean triggered;

    /** 严重度（INFO/YELLOW/RED） */
    private String severity;

    /** 条件表达式或值（参考） */
    private String conditionResult;

    /** 评估耗时（毫秒） */
    private long elapsedMs;

    /** 事实快照（JSON） */
    private Map<String, Object> factsSnapshot;

    /** 结果快照（JSON） */
    private Map<String, Object> resultSnapshot;

    /** 错误信息（评估异常时） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    public RuleExecutionTrace() {
    }

    public RuleExecutionTrace(String traceId, String ruleCode, String ruleName, String scenario,
                              boolean triggered, String severity, String conditionResult,
                              long elapsedMs, Map<String, Object> factsSnapshot,
                              Map<String, Object> resultSnapshot, String errorMessage) {
        this.traceId = traceId;
        this.ruleCode = ruleCode;
        this.ruleName = ruleName;
        this.scenario = scenario;
        this.triggered = triggered;
        this.severity = severity;
        this.conditionResult = conditionResult;
        this.elapsedMs = elapsedMs;
        this.factsSnapshot = factsSnapshot;
        this.resultSnapshot = resultSnapshot;
        this.errorMessage = errorMessage;
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getConditionResult() { return conditionResult; }
    public void setConditionResult(String conditionResult) { this.conditionResult = conditionResult; }
    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }
    public Map<String, Object> getFactsSnapshot() { return factsSnapshot; }
    public void setFactsSnapshot(Map<String, Object> factsSnapshot) { this.factsSnapshot = factsSnapshot; }
    public Map<String, Object> getResultSnapshot() { return resultSnapshot; }
    public void setResultSnapshot(Map<String, Object> resultSnapshot) { this.resultSnapshot = resultSnapshot; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
