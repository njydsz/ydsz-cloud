package com.njydsz.gateway.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.njydsz.common.json.YdszJson;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 网关 Sentinel 熔断降级规则配置（P0-阶段二-2 / P0-阶段二-3 / P0-阶段二-4）
 *
 * <p>对标大厂网关的"三级保护"体系：
 * <ol>
 *   <li><b>DegradeRule 熔断降级</b>（P0-阶段二-2）：基于异常比例 / 慢调用比例触发熔断，
 *       避免下游服务雪崩拖垮网关线程池</li>
 *   <li><b>SystemRule 系统自适应保护</b>（P0-阶段二-3）：基于 LOAD / RT / 线程数 / 入口 QPS
 *       的自适应保护，避免网关自身过载</li>
 *   <li><b>Nacos 动态数据源</b>（P0-阶段二-4）：规则持久化到 Nacos 配置中心，
 *       运维修改规则后实时推送到所有网关实例，无需重启</li>
 * </ol>
 *
 * <h3>DegradeRule 默认策略</h3>
 * <ul>
 *   <li>异常比例熔断：5 秒内请求数 ≥ 10，异常比例 ≥ 50% → 熔断 30 秒</li>
 *   <li>慢调用熔断：RT &gt; 3s 占比 ≥ 50% → 熔断 30 秒</li>
 * </ul>
 *
 * <h3>SystemRule 默认策略</h3>
 * <ul>
 *   <li>入口 QPS 上限：5000（防瞬时洪峰）</li>
 *   <li>平均 RT 上限：100ms（防慢调用堆积）</li>
 *   <li>线程数上限：500（防线程池打满）</li>
 *   <li>LOAD 上限：128（按 64 核基准的 2 倍）</li>
 * </ul>
 *
 * <h3>Nacos 数据源</h3>
 * <p>规则存储在 Nacos 配置：
 * <ul>
 *   <li>{@code ydsz-gateway-sentinel-degrade.json} — 熔断降级规则</li>
 *   <li>{@code ydsz-gateway-sentinel-system.json} — 系统保护规则</li>
 * </ul>
 * 配置不存在时使用代码内默认规则，确保网关启动即有保护。
 *
 * @since 1.0.0
 */
@Slf4j
@Configuration
@Data
public class GatewaySentinelRulesConfig {

    /**
     * 是否启用 Nacos 数据源（P0-阶段二-4）
     *
     * <p>启用后从 Nacos 拉取 Sentinel 规则，关闭则仅使用代码内默认规则。
     */
    @Value("${ydsz.gateway.sentinel.nacos-datasource-enabled:false}")
    private boolean nacosDatasourceEnabled;

    /**
     * Nacos 中 Sentinel 规则的 DataId（熔断降级）
     */
    @Value("${ydsz.gateway.sentinel.degrade-rule-data-id:ydsz-gateway-sentinel-degrade.json}")
    private String degradeRuleDataId;

    /**
     * Nacos 中 Sentinel 规则的 DataId（系统保护）
     */
    @Value("${ydsz.gateway.sentinel.system-rule-data-id:ydsz-gateway-sentinel-system.json}")
    private String systemRuleDataId;

    /**
     * Nacos 中 Sentinel 规则的 Group
     */
    @Value("${ydsz.gateway.sentinel.rule-group:${spring.cloud.nacos.config.group:DEFAULT_GROUP}}")
    private String ruleGroup;

    /**
     * 初始化 Sentinel 规则
     *
     * <p>启动顺序：
     * <ol>
     *   <li>注册代码内默认规则（兜底）</li>
     *   <li>若启用 Nacos 数据源，注册动态数据源覆盖默认规则</li>
     * </ol>
     */
    @PostConstruct
    public void init() {
        // 1. 注册默认规则（代码内兜底）
        registerDefaultDegradeRules();
        registerDefaultSystemRules();

        // 2. 启用 Nacos 数据源（动态推送）
        if (nacosDatasourceEnabled) {
            registerNacosDataSources();
        } else {
            log.info("[SentinelRules] Nacos 数据源未启用 (ydsz.gateway.sentinel.nacos-datasource-enabled=false)，仅使用代码内默认规则");
        }
    }

    /**
     * P0-阶段二-2: 注册默认熔断降级规则
     *
     * <p>兜底规则，确保即使 Nacos 未配置也能提供基础保护。
     */
    private void registerDefaultDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        // 规则 1: 异常比例熔断（5 秒内 ≥ 10 请求，异常率 ≥ 50% → 熔断 30 秒）
        DegradeRule exceptionRatioRule = new DegradeRule();
        exceptionRatioRule.setResource("ydsz-userinfo");
        exceptionRatioRule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
        exceptionRatioRule.setCount(0.5);
        exceptionRatioRule.setTimeWindow(30);
        exceptionRatioRule.setMinRequestAmount(10);
        exceptionRatioRule.setStatIntervalMs(5000);
        rules.add(exceptionRatioRule);

        // 规则 2: 慢调用比例熔断（RT > 3s 占比 ≥ 50% → 熔断 30 秒）
        DegradeRule slowCallRule = new DegradeRule();
        slowCallRule.setResource("ydsz-system");
        slowCallRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        slowCallRule.setCount(3000);
        slowCallRule.setTimeWindow(30);
        slowCallRule.setMinRequestAmount(5);
        slowCallRule.setStatIntervalMs(5000);
        slowCallRule.setSlowRatioThreshold(0.5);
        rules.add(slowCallRule);

        DegradeRuleManager.loadRules(rules);
        log.info("[SentinelRules] 默认熔断降级规则已加载 count={} (P0-阶段二-2: 异常比例/慢调用熔断)", rules.size());
    }

    /**
     * P0-阶段二-3: 注册默认系统自适应保护规则
     *
     * <p>对标阿里 Sentinel 官方推荐配置，防网关自身过载。
     */
    private void registerDefaultSystemRules() {
        List<SystemRule> rules = new ArrayList<>();

        // 规则 1: 入口 QPS 上限 5000（防瞬时洪峰）
        SystemRule qpsRule = new SystemRule();
        qpsRule.setQps(5000);
        rules.add(qpsRule);

        // 规则 2: 平均 RT 上限 100ms（防慢调用堆积）
        SystemRule rtRule = new SystemRule();
        rtRule.setAvgRt(100);
        rules.add(rtRule);

        // 规则 3: 线程数上限 500（防线程池打满）
        SystemRule threadRule = new SystemRule();
        threadRule.setMaxThread(500);
        rules.add(threadRule);

        // 规则 4: LOAD 上限 128（按 64 核基准的 2 倍）
        SystemRule loadRule = new SystemRule();
        loadRule.setHighestSystemLoad(128.0);
        rules.add(loadRule);

        SystemRuleManager.loadRules(rules);
        log.info("[SentinelRules] 默认系统保护规则已加载 count={} (P0-阶段二-3: QPS/RT/线程/LOAD 自适应保护)", rules.size());
    }

    /**
     * P0-阶段二-4: 注册 Nacos 数据源（动态推送）
     *
     * <p>从 Nacos 配置中心拉取 Sentinel 规则，运维修改后实时生效。
     */
    private void registerNacosDataSources() {
        String serverAddr = System.getProperty(PropertyKeyConst.SERVER_ADDR,
                System.getenv().getOrDefault("NACOS_SERVER_ADDR", "127.0.0.1:8848"));
        String namespace = System.getProperty(PropertyKeyConst.NAMESPACE,
                System.getenv().getOrDefault("NACOS_NAMESPACE", "ydsz"));

        Properties nacosProps = new Properties();
        nacosProps.put(PropertyKeyConst.SERVER_ADDR, serverAddr);
        nacosProps.put(PropertyKeyConst.NAMESPACE, namespace);

        // 熔断降级规则数据源
        Converter<String, List<DegradeRule>> degradeConverter = source -> YdszJson.parseArray(source, DegradeRule.class);
        ReadableDataSource<String, List<DegradeRule>> degradeDs = new NacosDataSource<>(
                nacosProps, ruleGroup, degradeRuleDataId, degradeConverter);
        DegradeRuleManager.register2Property(degradeDs.getProperty());

        // 系统保护规则数据源
        Converter<String, List<SystemRule>> systemConverter = source -> YdszJson.parseArray(source, SystemRule.class);
        ReadableDataSource<String, List<SystemRule>> systemDs = new NacosDataSource<>(
                nacosProps, ruleGroup, systemRuleDataId, systemConverter);
        SystemRuleManager.register2Property(systemDs.getProperty());

        log.info("[SentinelRules] Nacos 数据源已注册 (P0-阶段二-4: degrade={}, system={}, group={}, addr={})",
                degradeRuleDataId, systemRuleDataId, ruleGroup, serverAddr);
    }
}
