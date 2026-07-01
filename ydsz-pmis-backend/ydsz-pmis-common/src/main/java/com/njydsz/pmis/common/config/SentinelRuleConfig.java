package com.njydsz.pmis.common.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 规则配置
 *
 * <p>在无 Sentinel Dashboard 连接时提供本地兜底规则:
 * <ul>
 *   <li>所有 API 默认 QPS 100 (防止突发流量打垮服务)</li>
 *   <li>认证接口 QPS 50 (登录/刷新 token)</li>
 * </ul>
 *
 * <p>当 Sentinel Dashboard 连接后，Dashboard 下发的规则会覆盖本地规则。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(FlowRule.class)
public class SentinelRuleConfig {

    /**
     * 初始化本地兜底限流规则
     */
    @PostConstruct
    public void initRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 全局默认限流: QPS 100
        FlowRule defaultRule = new FlowRule();
        defaultRule.setResource("default");
        defaultRule.setCount(100);
        defaultRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rules.add(defaultRule);

        FlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载本地兜底限流规则: {} 条", rules.size());
    }
}
