package com.njydsz.pmis.literule.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 规则评估上下文
 *
 * <p>封装规则评估所需的全部输入数据（事实快照），以 key-value 形式提供。
 * 表达式引擎通过变量名从上下文中取值。不可变（防御性拷贝）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class RuleContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事实数据快照 */
    private final Map<String, Object> facts;

    /** 业务场景标识（如 COCKPIT / BUDGET_CHECK / CLOSURE_ADMISSION） */
    private final String scenario;

    /** 触发来源（如定时任务/接口调用/事件监听，用于审计追踪） */
    private final String source;

    /** 追踪 ID（同一批次评估共享，用于链路追踪） */
    private final String traceId;

    private RuleContext(Map<String, Object> facts, String scenario, String source, String traceId) {
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        this.scenario = scenario;
        this.source = source;
        this.traceId = traceId;
    }

    /**
     * 从 Map 构建上下文
     *
     * @param facts    事实数据
     * @param scenario 业务场景
     * @param source   触发来源
     * @param traceId  追踪 ID
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts, String scenario, String source, String traceId) {
        Objects.requireNonNull(facts, "facts 不能为 null");
        return new RuleContext(facts, scenario, source, traceId);
    }

    /**
     * 从 Map 构建上下文
     *
     * @param facts    事实数据
     * @param scenario 业务场景
     * @param source   触发来源
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts, String scenario, String source) {
        return of(facts, scenario, source, java.util.UUID.randomUUID().toString());
    }

    /**
     * 从 Map 构建上下文（默认场景为 DEFAULT）
     *
     * @param facts 事实数据
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts) {
        return of(facts, "DEFAULT", "UNKNOWN", java.util.UUID.randomUUID().toString());
    }

    /**
     * 获取指定 key 的事实值
     *
     * @param key 事实键
     * @return 事实值；不存在返回 null
     */
    public Object get(String key) {
        return facts.get(key);
    }

    /**
     * 获取全部事实数据（只读）
     *
     * @return 不可修改的 Map
     */
    public Map<String, Object> getFacts() {
        return facts;
    }

    public String getScenario() { return scenario; }
    public String getSource() { return source; }
    public String getTraceId() { return traceId; }

    @Override
    public String toString() {
        return "RuleContext{scenario='" + scenario + "', source='" + source + "', facts=" + facts + "}";
    }
}
