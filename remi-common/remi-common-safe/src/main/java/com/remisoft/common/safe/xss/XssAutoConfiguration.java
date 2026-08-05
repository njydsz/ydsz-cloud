package com.remisoft.common.safe.xss;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.remisoft.common.json.module.JsonModule;

/**
 * XSS JSON 自动配置。
 *
 * <p>当 {@code remi.safe.xss.enabled=true} 时，通过 {@link SafeJsonModule}（JsonModule SPI）
 * 自动将 {@link XssStringDeserializer} 注册到全局 RemiJson 引擎，
 * 使 JSON 反序列化时自动对 String 字段进行 XSS 清洗。
 *
 * <p>注册方式由原来的手动 {@code RemiJson.register()} 改为 Spring Bean 自动发现，
 * 符合框架自动装配规范，与 {@code JsonAutoConfiguration} 的模块注册流程统一。
 *
 * @author remi-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.safe.xss", name = "enabled", havingValue = "true", matchIfMissing = false)
public class XssAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(XssAutoConfiguration.class);

    /**
     * 注册 Safe JSON 模块（JsonModule SPI）。
     *
     * <p>此 Bean 会被 {@code JsonAutoConfiguration} 中的
     * {@code JsonModuleRegistrar} 自动发现并注册到 RemiJson 引擎。
     *
     * @return Safe JSON 模块实例
     */
    @Bean
    public JsonModule safeJsonModule() {
        log.debug("[XssAutoConfiguration] Registering SafeJsonModule via JsonModule SPI");
        return new SafeJsonModule();
    }
}
