package com.njydsz.pmis.common.feign.circuitbreaker;

import com.njydsz.pmis.common.feign.config.FeignProperties;
import com.njydsz.pmis.common.feign.config.FeignConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Resilience4j 熔断器自动配置类。
 *
 * <p>当 classpath 中存在 Resilience4j 且配置启用时，自动注册 Resilience4j 熔断器策略。
 * 优先级高于 Sentinel 和滑动窗口策略，确保 Resilience4j 作为首选熔断方案。
 *
 * <p><b>生效条件：</b>
 * <ul>
 *   <li>classpath 中存在 {@code io.github.resilience4j.circuitbreaker.CircuitBreaker}</li>
 *   <li>{@code remi.feign.circuit-breaker.enabled=true}</li>
 *   <li>尚未注册其他 {@link FeignCircuitBreakerStrategy} Bean</li>
 * </ul>
 *
 * <p><b>优先级：</b>通过 {@code @AutoConfigureAfter(FeignConfiguration.class)} 显式声明在
 * {@link FeignConfiguration} 之后加载，这样本类的 {@code @ConditionalOnMissingBean} 才会
 * 在 Sentinel 策略已注册时跳过，确保 Resilience4j 始终优先。</p>
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>
 * remi:
 *   feign:
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 50
 *       slow-call-rate-threshold: 100
 *       slow-call-duration-threshold: 3000
 *       permitted-number-of-calls-in-half-open-state: 10
 *       sliding-window-size: 100
 *       sliding-window-type: COUNT_BASED
 *       wait-duration-in-open-state: 60
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see Resilience4jCircuitBreakerAdapter
 * @see FeignProperties.CircuitBreaker
 */
@AutoConfiguration(after = FeignConfiguration.class)
@ConditionalOnClass(io.github.resilience4j.circuitbreaker.CircuitBreaker.class)
@ConditionalOnProperty(prefix = "remi.feign.circuit-breaker", name = "enabled", havingValue = "true")
public class Resilience4jFeignConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jFeignConfiguration.class);

    /**
     * 注册 Resilience4j 熔断器策略 Bean。
     *
     * <p>使用 {@code @ConditionalOnMissingBean} 确保仅在没有其他策略注册时生效，
     * 避免与 Sentinel 或滑动窗口策略冲突。
     *
     * @param properties Feign 配置属性
     * @return Resilience4jCircuitBreakerAdapter 实例
     */
    @Bean
    @ConditionalOnMissingBean(FeignCircuitBreakerStrategy.class)
    public FeignCircuitBreakerStrategy resilience4jCircuitBreakerStrategy(FeignProperties properties) {
        log.info("[Feign] 使用 Resilience4j 熔断器策略");
        return new Resilience4jCircuitBreakerAdapter(properties);
    }
}
