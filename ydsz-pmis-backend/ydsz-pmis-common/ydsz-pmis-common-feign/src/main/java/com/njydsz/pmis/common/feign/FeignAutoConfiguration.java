package com.njydsz.pmis.common.feign;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Feign 通信层自动配置
 *
 * <p>聚合 feign 模块所有配置类，通过 Spring Boot 3 自动装配机制注册。
 * 引入 {@code ydsz-pmis-common-feign} 依赖后自动生效。
 *
 * <p>包含：
 * <ul>
 *   <li>{@link PmisFeignLogger} - Feign 日志 + Micrometer 指标</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Configuration
@ConditionalOnClass(name = "feign.Logger")
@Import(PmisFeignLogger.class)
public class FeignAutoConfiguration {
}
