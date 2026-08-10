package com.njydsz.common.exception.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 异常模块核心自动配置
 *
 * <p>合并了原有的 3 个基础配置类：
 * <ul>
 *   <li>国际化核心：{@link MessageSource}、{@link Validator}、消息解析器注入</li>
 *   <li>Web 国际化：{@link LocaleResolver}、{@link LocaleChangeInterceptor}</li>
 *   <li>异常指标：{@link ExceptionMetrics}</li>
 * </ul>
 *
 * <p>所有 Web/Actuator 相关能力均通过 {@code @ConditionalOnClass} 条件加载，
 * 保证在纯后端（无 Web 容器）场景下也能使用异常模块的核心功能。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties({I18nProperties.class, ExceptionProperties.class})
@ConditionalOnClass(name = "org.springframework.context.MessageSource")
public class YdszExceptionCoreAutoConfiguration {

    /** MessageSource Bean 名称常量 */
    public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

    private final I18nProperties i18nProperties;
    private final ExceptionProperties exceptionProperties;
    private final Environment environment;
    private final ObjectProvider<MessageSource> messageSourceProvider;

    /** Spring 应用上下文，用于延迟获取 ErrorCodeTable Bean */
    private ApplicationContext applicationContext;

    public YdszExceptionCoreAutoConfiguration(I18nProperties i18nProperties,
                                               ExceptionProperties exceptionProperties,
                                               ObjectProvider<Environment> environmentProvider,
                                               ObjectProvider<MessageSource> messageSourceProvider) {
        this.i18nProperties = i18nProperties;
        this.exceptionProperties = exceptionProperties;
        this.environment = environmentProvider.getIfAvailable();
        this.messageSourceProvider = messageSourceProvider;
    }

    /**
     * 由 Spring 注入 ApplicationContext。
     */
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    // ==================== 国际化核心 ====================

    /**
     * 在 Bean 初始化完成后桥接 ErrorCodeTable 与兼容门面。
     *
     * <p>将 {@link ErrorCodeTable} 注入到 {@link ExceptionCodeRegistry}，
     * 使历史静态门面自动委托 ErrorCodeTable，实现双写兼容。
     *
     * <p><b>注意：</b>国际化消息解析已迁移至 Handler 层直接使用 MessageSource，
     * 不再通过 {@link AbstractYdszException} 内部解析器注入。
     */
    @PostConstruct
    public void injectMessageResolver() {
        MessageSource messageSource = messageSourceProvider.getIfAvailable();
        if (messageSource == null) {
            log.warn("MessageSource 未找到，异常消息将降级为返回 i18n key");
            return;
        }
        log.info("异常模块已就绪 | MessageSource: {}（i18n 解析由 Handler 层处理）", messageSource.getClass().getSimpleName());
    }

    /**
     * 创建全局国际化消息源，并在启动阶段对异常错误码的 i18n key 做 fail-fast 校验。
     */
    @Bean(name = MESSAGE_SOURCE_BEAN_NAME)
    @ConditionalOnMissingBean(name = MESSAGE_SOURCE_BEAN_NAME)
    public MessageSource messageSource() {
        boolean isProd = isProdEnvironment();
        int cacheSeconds = isProd ? i18nProperties.getProdCacheSeconds() : i18nProperties.getDevCacheSeconds();
        MessageSource messageSource = createMessageSource(cacheSeconds);
        boolean validateEnabled = environment == null
                || environment.getProperty("ydsz.i18n.validate-on-startup", Boolean.class, true);
        if (validateEnabled) {
            validateExceptionCodeKeys(messageSource);
        }
        return messageSource;
    }

    /**
     * 注册关联国际化消息源的 JSR-303 验证器。
     */
    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public Validator getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        log.info("验证器已关联国际化消息源");
        return validator;
    }

    // ==================== Web 国际化 ====================

    /**
     * 创建区域解析器 Bean。
     */
    @Bean
    @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.CHINA);

        List<Locale> localeList = new ArrayList<>();
        for (String localeStr : i18nProperties.getSupportedLocales()) {
            String[] parts = localeStr.split("_");
            if (parts.length == 2) {
                localeList.add(new Locale.Builder().setLanguage(parts[0]).setRegion(parts[1]).build());
            } else {
                localeList.add(Locale.CHINA);
            }
        }
        resolver.setSupportedLocales(localeList);
        return resolver;
    }

    /**
     * 创建语言切换拦截器 Bean。
     */
    @Bean
    @ConditionalOnClass({LocaleResolver.class, AcceptHeaderLocaleResolver.class})
    @ConditionalOnMissingBean(LocaleChangeInterceptor.class)
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(i18nProperties.getLangParamName());
        return interceptor;
    }

    // ==================== 异常指标 ====================

    /**
     * 注册异常指标统计器。
     */
    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "ydsz.exception", name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ExceptionMetrics.class)
    public ExceptionMetrics exceptionMetrics(MeterRegistry meterRegistry) {
        ExceptionMetrics metrics = new ExceptionMetrics(meterRegistry);
        metrics.setIncludeCodeTag(exceptionProperties.isMetricsIncludeCodeTag());
        return metrics;
    }

    // ==================== 辅助方法 ====================

    private boolean isProdEnvironment() {
        String[] activeProfiles = environment != null ? environment.getActiveProfiles() : new String[]{};
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private MessageSource createMessageSource(int cacheSeconds) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();

        String basename = i18nProperties.getBasename();
        String[] basenameArray = basename.split(",");
        List<String> basenameList = new ArrayList<>();
        for (String bn : basenameArray) {
            basenameList.add(bn.trim());
        }
        String i18nBaseNames = i18nProperties.getI18nBaseNames();
        if (i18nBaseNames != null && !i18nBaseNames.isEmpty()) {
            String[] i18nArray = i18nBaseNames.split(",");
            for (String bn : i18nArray) {
                String trimmed = bn.trim();
                if (!trimmed.isEmpty()) {
                    basenameList.add(trimmed.startsWith("classpath:") ? trimmed : "classpath:" + trimmed);
                }
            }
        }
        messageSource.setBasenames(basenameList.toArray(new String[0]));

        messageSource.setDefaultEncoding(i18nProperties.getEncoding());
        messageSource.setCacheSeconds(cacheSeconds);
        messageSource.setFallbackToSystemLocale(i18nProperties.isFallbackToSystemLocale());
        messageSource.setUseCodeAsDefaultMessage(true);

        validateAndLogConfig(messageSource, cacheSeconds);

        return messageSource;
    }

    private void validateAndLogConfig(MessageSource messageSource, int cacheSeconds) {
        try {
            messageSource.getMessage("test.key", null, Locale.CHINA);
            log.info("国际化配置加载成功 | 基础路径: {} | 缓存时间: {}秒 | 支持语言: {}",
                    i18nProperties.getBasename(), cacheSeconds, Arrays.toString(i18nProperties.getSupportedLocales()));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("No message found under key 'test.key'")) {
                log.warn("国际化配置文件加载成功，但未找到测试key: test.key（非必选）");
            } else {
                log.error("国际化配置加载检查异常", e);
            }
        }
    }

    /**
     * 启动时校验所有已注册的 ExceptionCode 的 i18n key 是否能在默认 messages.properties 中解析。
     *
     * <p>优先从 ErrorCodeTable 获取注册信息，回退到 ExceptionCodeRegistry 兼容路径。
     */
    private void validateExceptionCodeKeys(MessageSource messageSource) {
        List<String> missingKeys = new ArrayList<>();

        // 从 ErrorCodeTable（统一注册表）获取已注册 code
        ErrorCodeTable errorCodeTable = applicationContext != null
                ? applicationContext.getBeanProvider(ErrorCodeTable.class).getIfAvailable()
                : null;
        if (errorCodeTable == null) {
            log.warn("ErrorCodeTable 尚未就绪，跳过 i18n key 启动校验");
            return;
        }
        Map<String, ExceptionCode> registered = errorCodeTable.allCodes();

        for (Map.Entry<String, ExceptionCode> entry : registered.entrySet()) {
            collectMissingKey(messageSource, entry.getValue(), missingKeys);
        }

        if (!missingKeys.isEmpty()) {
            String errorMsg = String.format(
                    "i18n 启动校验失败：以下 %d 个 ExceptionCode 的 key 在 messages.properties 中缺失，"
                    + "请检查 src/main/resources/i18n/messages.properties 及对应语言文件：\n  - %s\n"
                    + "如需关闭校验，可设置 ydsz.i18n.validate-on-startup=false（不推荐）。",
                    missingKeys.size(), String.join("\n  - ", missingKeys));
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        log.info("i18n 启动校验通过：共 {} 个 ExceptionCode key 全部可在 messages.properties 中解析",
                registered.size());
    }

    private void collectMissingKey(MessageSource messageSource, ExceptionCode code, List<String> missingKeys) {
        String key = code.getKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            String message = messageSource.getMessage(key, null, null, Locale.ROOT);
            if (message == null || message.equals(key)) {
                missingKeys.add(key + " (code=" + code.getCode() + ")");
            }
        } catch (Exception e) {
            missingKeys.add(key + " (code=" + code.getCode() + ", error=" + e.getMessage() + ")");
        }
    }
}
