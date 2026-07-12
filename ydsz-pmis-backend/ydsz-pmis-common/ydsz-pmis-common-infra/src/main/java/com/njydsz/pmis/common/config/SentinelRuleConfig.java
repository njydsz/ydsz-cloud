package com.njydsz.pmis.common.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则配置
 *
 * <p>在无 Sentinel Dashboard 连接时提供本地兜底规则（大厂标准三类规则）:
 * <ul>
 *   <li>QPS 限流: 核心接口分级限流，防止突发流量打垮服务</li>
 *   <li>异常比例熔断: 5xx 比例 > 50% 持续 10s 触发熔断 30s</li>
 *   <li>RT 熔断: P95 > 1s 持续 10s 触发熔断 30s</li>
 *   <li>系统规则: CPU > 80% / Load > 4 触发系统级保护</li>
 * </ul>
 *
 * <p>当 Sentinel Dashboard 连接后，Dashboard 下发的规则会覆盖本地规则。
 * 推荐通过 Nacos + Sentinel Dashboard 集中下发规则，本配置仅作兜底。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(FlowRule.class)
public class SentinelRuleConfig {

    /** 慢调用阈值（毫秒），超过即视为慢调用 */
    private static final long SLOW_RT_THRESHOLD_MS = 1000;
    /** 慢调用比例阈值（0~1），超过即触发熔断 */
    private static final double SLOW_CALL_RATIO = 0.5;
    /** 异常比例阈值（0~1），超过即触发熔断 */
    private static final double ERROR_RATIO = 0.5;
    /** 熔断时长（秒） */
    private static final int CIRCUIT_BREAKER_SECONDS = 30;
    /** 最小请求数（统计窗口内达不到该数不熔断，避免误判） */
    private static final int MIN_REQUESTS = 5;
    /** 统计窗口时长（秒） */
    private static final int STAT_INTERVAL_SECONDS = 10;

    /**
     * 初始化本地兜底限流/熔断规则
     * <p>覆盖核心接口: 认证、合同、付款、报表、AI 等关键路径
     */
    @PostConstruct
    public void initRules() {
        loadFlowRules();
        loadDegradeRules();
        loadSystemRules();
    }

    /**
     * QPS 限流规则
     * <p>按接口重要性分级:
     * <ul>
     *   <li>认证接口（登录/刷新）: 50 QPS（防暴力枚举）</li>
     *   <li>核心业务接口（合同/付款）: 100 QPS</li>
     *   <li>报表/导出/AI: 20 QPS（防止拖垮 DB/LLM）</li>
     *   <li>默认: 200 QPS</li>
     * </ul>
     */
    private void loadFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 认证接口（防暴力枚举）
        rules.add(buildFlowRule("POST:/auth/login", 50));
        rules.add(buildFlowRule("POST:/auth/refresh", 50));
        rules.add(buildFlowRule("POST:/auth/logout", 100));

        // 核心业务接口
        rules.add(buildFlowRule("POST:/contracts", 100));
        rules.add(buildFlowRule("POST:/payments", 100));
        rules.add(buildFlowRule("POST:/initiations", 100));
        rules.add(buildFlowRule("POST:/projects", 100));

        // 报表/导出/AI（重负载，需限流保护）
        rules.add(buildFlowRule("GET:/reports", 20));
        rules.add(buildFlowRule("GET:/cockpit", 20));
        rules.add(buildFlowRule("POST:/exports", 10));
        rules.add(buildFlowRule("POST:/agent/orchestration", 10));
        rules.add(buildFlowRule("POST:/agent/prediction", 10));

        // 全局默认
        rules.add(buildFlowRule("default", 200));

        FlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载本地兜底 QPS 限流规则: {} 条", rules.size());
    }

    /**
     * 熔断降级规则（异常比例 + 慢调用比例）
     * <p>对核心接口配置两种熔断策略，任一触发即熔断 30s
     */
    private void loadDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 核心接口：异常比例熔断
        String[] criticalResources = {
                "POST:/auth/login",
                "POST:/contracts",
                "POST:/payments",
                "POST:/initiations",
                "GET:/reports",
                "GET:/cockpit"
        };
        for (String resource : criticalResources) {
            // 异常比例熔断
            rules.add(buildExceptionRatioRule(resource));
            // 慢调用比例熔断
            rules.add(buildSlowCallRule(resource));
        }

        DegradeRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载本地兜底熔断规则: {} 条（异常比例 + 慢调用 RT）", rules.size());
    }

    /**
     * 系统级保护规则
     * <p>CPU > 80% / Load > 4 触发系统级限流，避免雪崩
     */
    private void loadSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        // CPU 使用率阈值
        SystemRule cpuRule = new SystemRule();
        cpuRule.setHighestCpuUsage(0.8);
        rules.add(cpuRule);

        // 系统负载阈值（仅 Linux 生效）
        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(4.0);
        rules.add(loadRule);

        // 平均 RT 阈值（ms）
        SystemRule rtRule = new SystemRule();
        rtRule.setAvgRt(500);
        rules.add(rtRule);

        // 入口 QPS 阈值
        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(2000);
        rules.add(qpsRule);

        SystemRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载系统级保护规则: {} 条（CPU/Load/RT/QPS）", rules.size());
    }

    /**
     * 构建单条 QPS 限流规则。
     *
     * <p>控制行为：预热 + 排队等待（WARM_UP_RATE_LIMITER），预热时长 10s，排队最大等待 1s。
     * 超出排队等待时间的请求被拒绝。</p>
     *
     * @param resource 资源名（HTTP 方法:路径，如 {@code "POST:/auth/login"}）
     * @param qps      允许的 QPS 上限
     * @return FlowRule 实例
     */
    private FlowRule buildFlowRule(String resource, int qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setCount(qps);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setLimitApp("default");
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP_RATE_LIMITER);
        // 预热时长 10s，避免冷启动击穿
        rule.setWarmUpPeriodSec(10);
        // 排队等待最大时长 1s，超出即拒绝
        rule.setMaxQueueingTimeMs(1000);
        return rule;
    }

    /**
     * 异常比例熔断规则
     * <p>统计窗口 10s 内请求数 >= 5 时，若异常比例 > 50% 持续 10s，熔断 30s
     */
    private DegradeRule buildExceptionRatioRule(String resource) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        rule.setCount(ERROR_RATIO);
        rule.setTimeWindow(CIRCUIT_BREAKER_SECONDS);
        rule.setStatIntervalMs(STAT_INTERVAL_SECONDS * 1000);
        rule.setMinRequestAmount(MIN_REQUESTS);
        return rule;
    }

    /**
     * 慢调用比例熔断规则
     * <p>RT > 1s 视为慢调用，统计窗口 10s 内慢调用比例 > 50% 持续 10s，熔断 30s
     */
    private DegradeRule buildSlowCallRule(String resource) {
        DegradeRule rule = new DegradeRule(resource);
        rule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        rule.setCount(SLOW_RT_THRESHOLD_MS);
        rule.setSlowRatioThreshold(SLOW_CALL_RATIO);
        rule.setTimeWindow(CIRCUIT_BREAKER_SECONDS);
        rule.setStatIntervalMs(STAT_INTERVAL_SECONDS * 1000);
        rule.setMinRequestAmount(MIN_REQUESTS);
        return rule;
    }
}
