package com.njydsz.common.safe.ratelimit.spi;

import java.util.List;
import java.util.Optional;
import com.njydsz.common.safe.ratelimit.model.RateLimitRule;

/**
 * 限流规则提供器
 *
 * <p>抽象规则来源，支持多种实现：
 * <ul>
 *   <li>静态配置（{@code RateLimitProperties}）</li>
 *   <li>Nacos / Apollo 配置中心</li>
 *   <li>数据库（动态规则）</li>
 *   <li>API（运维手动修改）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RateLimitRuleProvider {

    /**
     * 根据资源名获取规则
     */
    Optional<RateLimitRule> getRule(String resource);

    /**
     * 获取所有规则
     */
    List<RateLimitRule> getAllRules();

    /**
     * 动态注册/更新规则
     */
    void saveRule(RateLimitRule rule);

    /**
     * 删除规则
     */
    void removeRule(String resource);

    /**
     * 监听规则变更
     */
    default void addListener(RateLimitRuleListener listener) {
    }
}
