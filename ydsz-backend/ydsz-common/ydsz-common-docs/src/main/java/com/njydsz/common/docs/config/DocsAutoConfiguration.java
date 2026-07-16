package com.njydsz.common.docs.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

import lombok.extern.slf4j.Slf4j;

/**
 * 文档处理模块自动配置类
 * <p>
 * 作为 ydsz-common-docs 模块的自动配置入口，
 * 通过 Spring Boot AutoConfiguration 机制激活文档解析、预处理、安全扫描和 PII 检测等能力。
 *
 * <p><b>配置开关：</b>
 * {@code ydsz.docs.enabled=true}（默认启用）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(DocsProperties.class)
@ConditionalOnProperty(prefix = "ydsz.docs", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.njydsz.common.docs")
public class DocsAutoConfiguration {

    public DocsAutoConfiguration(DocsProperties properties) {
        log.info("[DocsAutoConfiguration] 文档处理模块已启用 | 安全扫描={} | PII检测={} | 预处理={} | 水印={} | 脱敏={}",
                properties.isSecurityScanEnabled(),
                properties.isPiiDetectionEnabled(),
                properties.isPreprocessEnabled(),
                properties.isWatermarkEnabled(),
                properties.isRedactEnabled());
    }
}
