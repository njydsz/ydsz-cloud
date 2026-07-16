package com.njydsz.common.base.config;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import lombok.RequiredArgsConstructor;

/**
 * Knife4j 增强自动配置类
 *
 * <p>当 classpath 中存在 {@code com.github.xiaoymin.knife4j} 时自动激活，
 * 提供增强的 API 文档 UI 能力，包括：
 * <ul>
 *   <li>离线文档导出（HTML / Markdown / YAML / JSON）</li>
 *   <li>全局参数配置（如全局 Header、Authorize 等）</li>
 *   <li>增强的搜索、分组、个性化设置</li>
 * </ul>
 *
 * <p><b>线程安全性：</b>本类为无状态配置类，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.0.0
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(DocProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer")
@ConditionalOnProperty(prefix = "ydsz.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
public class Knife4jAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(Knife4jAutoConfiguration.class);

    /** 文档模块配置属性，由 Spring 注入 */
    private final DocProperties docProperties;

    /**
     * 初始化 Knife4j 配置
     *
     * <p>在 Bean 初始化完成后执行，输出 Knife4j 相关配置到日志。
     */
    @PostConstruct
    public void init() {
        logKnife4jConfig();
    }

    /**
     * 打印 Knife4j 配置信息到日志
     *
     * <p>在应用启动时输出文档增强相关配置（访问路径、导出开关、导出目录、支持格式等）。
     */
    private void logKnife4jConfig() {
        logger.info("========================================");
        logger.info("Knife4j 文档增强已启用");
        logger.info("  - 访问路径: {}", docProperties.getKnife4jPath());
        logger.info("  - 导出功能: {}", docProperties.getExport().isEnabled() ? "启用" : "禁用");
        if (docProperties.getExport().isEnabled()) {
            logger.info("  - 导出目录: {}", docProperties.getExport().getOutputDir());
            logger.info("  - 支持格式: {}", String.join(", ", docProperties.getExport().getFormats()));
        }
        logger.info("========================================");
    }
}
