package com.njydsz.common.safe.ratelimit.sentinel;

import java.time.Instant;
import java.util.ArrayList;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.algorithm.RateLimiter;
import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitResult;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * Sentinel 实现的限流器
 *
 * <p>基于阿里巴巴 Sentinel 的流控能力，支持：
 *
 * <ul>
 *   <li>QPS 限流（集群/本地模式）
 *   <li>并发线程数限流
 *   <li>warmup 预热限流
 *   <li>统一控制台管理规则
 * </ul>
 *
 * <p><b>启用方式：</b>在 application.yml 中配置 {@code ydsz.safe.ratelimit.sentinel-enabled=true} 并添加 {@code
 * sentinel-core} 依赖。
 *
 * <p><b>使用场景：</b>需要统一控制台管理限流规则、集群限流（配合 Sentinel Transport）或 默认令牌桶无法满足的复杂流控需求时使用。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
public class SentinelRateLimiter implements RateLimiter {

  private final RateLimitRule rule;

  public SentinelRateLimiter(RateLimitRule rule) {
    this.rule = rule;
    registerRule(rule);
  }

  @Override
  public RateLimitDecision tryAcquire(RateLimitContext context) {
    String resource = context.getResource();
    Entry entry = null;
    try {
      entry = SphU.entry(resource);
      return RateLimitDecision.builder()
          .result(RateLimitResult.PASS)
          .remaining((int) rule.getThreshold() - 1)
          .threshold(rule.getThreshold())
          .timestamp(Instant.now())
          .reason("sentinel pass")
          .build();
    } catch (BlockException e) {
      return RateLimitDecision.builder()
          .result(RateLimitResult.BLOCKED)
          .remaining(0)
          .threshold(rule.getThreshold())
          .waitTimeMillis(1000)
          .timestamp(Instant.now())
          .reason("sentinel blocked: " + e.getRuleLimitApp())
          .build();
    } finally {
      if (entry != null) {
        entry.exit();
      }
    }
  }

  @Override
  public RateLimitAlgorithm getAlgorithm() {
    return RateLimitAlgorithm.TOKEN_BUCKET;
  }

  @Override
  public RateLimitRule getRule() {
    return rule;
  }

  @Override
  public void reset() {
    // Sentinel 规则通过控制台管理，不支持运行时重置
  }

  /**
   * 注册 Sentinel 流控规则
   *
   * @param rule 限流规则
   */
  private void registerRule(RateLimitRule rule) {
    String resource = rule.getResource();
    if (FlowRuleManager.getRules().stream().anyMatch(r -> r.getResource().equals(resource))) {
      return;
    }
    FlowRule flowRule = new FlowRule(resource);
    flowRule.setCount(rule.getThreshold());
    flowRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
    flowRule.setLimitApp("default");
    ArrayList<FlowRule> rules = new ArrayList<>(FlowRuleManager.getRules());
    rules.add(flowRule);
    FlowRuleManager.loadRules(rules);
    log.info("注册 Sentinel 限流规则: resource={}, threshold={}", resource, rule.getThreshold());
  }
}
