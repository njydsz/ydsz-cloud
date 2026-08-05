package com.remisoft.common.core.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import com.remisoft.common.core.constant.PageConstants;

/**
 * Remi Core 模块自动配置
 *
 * <p>提供以下核心能力自动装配：
 * <ul>
 *   <li>分页参数运行时覆盖（{@link CorePageConfig} 实例注入）</li>
 *   <li>响应全局配置桥接（RFC 9457、timestamp 开关）</li>
 *   <li>独立的 Core 模块 i18n MessageSource（不污染根路径 messages）</li>
 *   <li>请求上下文策略组件注册</li>
 * </ul>
 *
 * <p>所有配置项均可通过 {@code remi.core.*} 路径在 application.yml 中覆盖。
 *
 * <h3>启用/禁用策略：</h3>
 * <ul>
 *   <li>全局禁用：{@code remi.core.enabled=false}</li>
 *   <li>仅禁用 RFC 9457：{@code remi.core.response.rfc9457.enabled=false}</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.8.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "remi.core", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(CoreProperties.class)
public class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    /**
     * Core 模块独立 i18n MessageSource。
     *
     * <p>使用独立的 basenames 路径 "i18n/core/messages"，不与业务模块 classpath:/messages 冲突。
     * 刷新间隔设为 1h，生产环境编译后可改为 -1（永不过期）以节省 CPU。
     *
     * @return Core 模块专属 MessageSource Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "coreMessageSource")
    public MessageSource coreMessageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames("classpath:i18n/core/messages");
        source.setDefaultEncoding("UTF-8");
        source.setCacheSeconds((int) Duration.ofHours(1).getSeconds());
        source.setFallbackToLocale(false);
        log.info("[remi-core] i18n MessageSource 已注册 | basenames=classpath:i18n/core/messages");
        return source;
    }

    /**
     * 注入受配置驱动的分页常量持有者
     *
     * <p>业务分页层可通过注入此 Bean 获取当前生效的 maxPageSize / defaultPageSize，
     * 不再需要直接引用 {@link PageConstants} 静态字段。
     *
     * @param properties 绑定的配置属性
     * @return 分页配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "corePageConfig")
    public CorePageConfig corePageConfig(CoreProperties properties) {
        CorePageConfig config = new CorePageConfig(
            properties.getPage().getDefaultPageNum(),
            properties.getPage().getDefaultPageSize(),
            properties.getPage().getMaxPageSize()
        );
        log.info("[remi-core] 分页配置已加载 | defaultPageNum={} | defaultPageSize={} | maxPageSize={}",
            config.defaultPageNum(), config.defaultPageSize(), config.maxPageSize());
        return config;
    }

    /**
     * 注入响应全局配置
     *
     * @param properties 绑定的配置属性
     * @return 响应配置 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "coreResponseConfig")
    public CoreResponseConfig coreResponseConfig(CoreProperties properties) {
        CoreProperties.Response rfcProps = properties.getResponse();
        CoreResponseConfig config = new CoreResponseConfig(
            rfcProps.isIncludeTimestamp(),
            rfcProps.getRfc9457().isEnabled(),
            rfcProps.getRfc9457().getTypeUriPrefix()
        );
        log.info("[remi-core] 响应配置已加载 | includeTimestamp={} | rfc9457.enabled={} | typeUriPrefix={}",
            config.includeTimestamp(), config.rfc9457Enabled(), config.rfc9457TypeUriPrefix());
        return config;
    }

    /**
     * 分页配置记录（对应用层暴露的可配置 API）
     *
     * @param defaultPageNum  默认页码（从 1 开始）
     * @param defaultPageSize 默认每页记录数
     * @param maxPageSize     允许的最大每页记录数
     */
    public record CorePageConfig(int defaultPageNum, int defaultPageSize, int maxPageSize) {

        /**
         * 校验并返回安全的 pageSize
         *
         * @param requested 请求的每页记录数（可为 0 表示使用默认值）
         * @return 受 maxPageSize 约束的安全值
         */
        public int safePageSize(int requested) {
            if (requested <= 0) {
                return defaultPageSize;
            }
            return Math.min(requested, maxPageSize);
        }

        /**
         * 计算指定页码的偏移量（Safe for long range）
         *
         * @param pageNum 页码（从 1 开始）
         * @param pageSize 已校验过的 pageSize
         * @return JDBC offset 偏移量
         */
        public long calcOffset(long pageNum, long pageSize) {
            long safePageNum = Math.max(pageNum, 1L);
            long safePageSize = Math.max(pageSize, 0L);
            return (safePageNum - 1) * safePageSize;
        }
    }

    /**
     * 响应全局配置记录
     *
     * @param includeTimestamp      是否在响应中包含 timestamp
     * @param rfc9457Enabled        是否启用 RFC 9457 响应格式
     * @param rfc9457TypeUriPrefix  RFC 9457 type URI 前缀
     */
    public record CoreResponseConfig(boolean includeTimestamp, boolean rfc9457Enabled, String rfc9457TypeUriPrefix) {
    }
}
