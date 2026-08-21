package com.njydsz.common.safe.ratelimit.provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.model.RateLimitRule;
import com.njydsz.common.safe.ratelimit.properties.RateLimitProperties;
import com.njydsz.common.safe.ratelimit.spi.RateLimitRuleListener;
import com.njydsz.common.safe.ratelimit.spi.RateLimitRuleProvider;

/**
 * 配置式规则提供器
 *
 * <p>从 {@link RateLimitProperties#rules} 加载静态规则，适用于简单场景。 复杂场景下可由 Nacos / Apollo / DB 提供器替换。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ConfigRuleProvider implements RateLimitRuleProvider {

  private final Map<String, RateLimitRule> ruleMap = new ConcurrentHashMap<>();
  private final List<RateLimitRuleListener> listeners = new CopyOnWriteArrayList<>();

  public ConfigRuleProvider(RateLimitProperties properties) {
    reload(properties);
  }

   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
   * @param properties properties 参数
  /** 从配置重新加载 */
  public void reload(RateLimitProperties properties) {
    ruleMap.clear();
    if (properties.getRules() != null) {
      for (RateLimitRule rule : properties.getRules()) {
        try {
          rule.validate();
          ruleMap.put(rule.getResource(), rule);
        } catch (Exception ex) {
          log.warn(
              "Invalid rate limit rule skipped: resource={}, error={}",
              rule.getResource(),
              ex.getMessage());
        }
      }
    }
    log.info("ConfigRuleProvider loaded {} rules", ruleMap.size());
  }

  @Override
  public Optional<RateLimitRule> getRule(String resource) {
    return Optional.ofNullable(ruleMap.get(resource));
  }

  @Override
  public List<RateLimitRule> getAllRules() {
    return ruleMap.values().stream().collect(Collectors.toList());
  }

  @Override
  public void saveRule(RateLimitRule rule) {
    rule.validate();
    boolean exists = ruleMap.containsKey(rule.getResource());
    ruleMap.put(rule.getResource(), rule);
    notifyListeners(
        rule,
        exists ? RateLimitRuleListener.ChangeType.UPDATED : RateLimitRuleListener.ChangeType.ADDED);
  }

  @Override
  public void removeRule(String resource) {
    RateLimitRule removed = ruleMap.remove(resource);
    if (removed != null) {
      notifyListeners(removed, RateLimitRuleListener.ChangeType.REMOVED);
    }
  }

  @Override
  public void addListener(RateLimitRuleListener listener) {
    listeners.add(listener);
  }

  private void notifyListeners(RateLimitRule rule, RateLimitRuleListener.ChangeType type) {
    for (RateLimitRuleListener listener : listeners) {
      try {
        listener.onRuleChanged(rule, type);
      } catch (Exception ex) {
        log.warn("Rate limit rule listener failed", ex);
      }
    }
  }
}
