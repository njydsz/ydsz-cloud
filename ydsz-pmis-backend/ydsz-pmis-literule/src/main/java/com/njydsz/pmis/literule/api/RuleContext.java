package com.njydsz.pmis.literule.api;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 规则评估上下文
 *
 * <p>封装规则评估所需的全部输入数据（事实快照），以 key-value 形式提供。
 * 表达式引擎通过变量名从上下文中取值。不可变（防御性拷贝）。
 *
 * <p>1.5.0 起新增 {@code tenantId} 字段，用于运行时租户隔离：
 * {@link com.njydsz.pmis.literule.core.DefaultRuleEngine} 在评估前会比较
 * {@code rule.getTenantId()} 与 {@code context.getTenantId()}，仅当两者匹配时才评估该规则。
 * 默认 "1"（单租户部署），向后兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class RuleContext implements Serializable {

    private static final String serialVersionUID = "1";

    /** 默认租户 ID（单租户部署） */
    private static final long DEFAULT_TENANT_ID = "1";

    /** 事实数据快照 */
    private final Map<String, Object> facts;

    /** 业务场景标识（如 COCKPIT / BUDGET_CHECK / CLOSURE_ADMISSION） */
    private final String scenario;

    /** 触发来源（如定时任务/接口调用/事件监听，用于审计追踪） */
    private final String source;

    /** 追踪 ID（同一批次评估共享，用于链路追踪） */
    private final String traceId;

    /** 租户 ID（运行时隔离，1.5.0 起） */
    private final String tenantId;

    private RuleContext(Map<String, Object> facts, String scenario, String source,
                        String traceId, String tenantId) {
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        this.scenario = scenario;
        this.source = source;
        this.traceId = traceId;
        this.tenantId = tenantId;
    }

    /**
     * 从 Map 构建上下文（指定租户）
     *
     * <p>1.5.0 起支持多租户运行时隔离：引擎仅评估 {@code rule.getTenantId() == tenantId} 的规则。
     *
     * @param facts    事实数据
     * @param scenario 业务场景
     * @param source   触发来源
     * @param traceId  追踪 ID
     * @param tenantId 租户 ID
     * @return RuleContext 实例
     * @since 1.5.0
     */
    public static RuleContext of(Map<String, Object> facts, String scenario, String source,
                                 String traceId, String tenantId) {
        Objects.requireNonNull(facts, "facts 不能为 null");
        return new RuleContext(facts, scenario, source, traceId, tenantId);
    }

    /**
     * 从 Map 构建上下文（默认租户 "1"）
     *
     * @param facts    事实数据
     * @param scenario 业务场景
     * @param source   触发来源
     * @param traceId  追踪 ID
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts, String scenario, String source, String traceId) {
        return of(facts, scenario, source, traceId, DEFAULT_TENANT_ID);
    }

    /**
     * 从 Map 构建上下文（默认租户 "1"）
     *
     * @param facts    事实数据
     * @param scenario 业务场景
     * @param source   触发来源
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts, String scenario, String source) {
        return of(facts, scenario, source, UUID.randomUUID().toString(), DEFAULT_TENANT_ID);
    }

    /**
     * 从 Map 构建上下文（默认场景为 DEFAULT、租户 "1"）
     *
     * @param facts 事实数据
     * @return RuleContext 实例
     */
    public static RuleContext of(Map<String, Object> facts) {
        return of(facts, "DEFAULT", "UNKNOWN", UUID.randomUUID().toString(), DEFAULT_TENANT_ID);
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

    /**
     * 获取租户 ID
     *
     * <p>引擎评估时仅放行 {@code rule.getTenantId() == this.tenantId} 的规则，
     * 默认 "1"（单租户部署，向后兼容）。
     *
     * @return 租户 ID；默认 "1"
     * @since 1.5.0
     */
    public long getTenantId() { return tenantId; }

    @Override
    public String toString() {
        return "RuleContext{scenario='" + scenario + "', source='" + source
                + "', tenantId=" + tenantId + ", facts=" + facts + "}";
    }
}
