package com.njydsz.common.safe.ratelimit.spi;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流规则提供器
 *
 * <p>抽象规则来源，支持多种实现：
 *
 * <ul>
 *   <li>静态配置（{@code RateLimitProperties}）
 *   <li>Nacos / Apollo 配置中心
 *   <li>数据库（动态规则）
 *   <li>API（运维手动修改）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface RateLimitRuleProvider {

  /**
   * 根据资源名获取规则。
   *
   * @param resource 资源标识
   * @return 限流规则（无匹配时为 {@code Optional.empty()}）
   */
  Optional<RateLimitRule> getRule(String resource);

  /**
   * 获取所有规则。
   *
   * @return 全量规则列表
   */
  List<RateLimitRule> getAllRules();

  /**
   * 动态注册/更新规则。
   *
   * @param rule 限流规则
   */
  void saveRule(RateLimitRule rule);

  /**
   * 删除规则。
   *
   * @param resource 资源标识
   */
  void removeRule(String resource);

  /**
   * 监听规则变更。
   *
   * @param listener 规则变更监听器
   */
  default void addListener(RateLimitRuleListener listener) {}
}
