package com.njydsz.pmis.common.exception.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.custom.AbstractYdszException;
import com.njydsz.pmis.common.exception.enums.ExceptionCode;
import com.njydsz.pmis.common.exception.enums.ExceptionCodeRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 国际化核心配置（不依赖 Spring MVC）
 *
 * <p>提供：
 * <ul>
 *   <li>{@link MessageSource} 多环境适配：开发环境实时加载，生产环境缓存</li>
 *   <li>{@link Validator} 关联 Hibernate Validator 与 i18n 消息</li>
 *   <li>异常模块的 {@link AbstractYdszException#setMessageResolver} 注入</li>
 * </ul>
 *
 * <p>Web MVC 相关（LocaleResolver/LocaleChangeInterceptor）由 {@link WebI18nConfiguration} 条件装配，
 * 避免 exception 模块对 spring-webmvc 的强依赖。</p>
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * spring:
 *   messages:
 *     basename: classpath:i18n/messages,classpath:i18n/errors
 *     encoding: UTF-8
 *     devCacheSeconds: 0
 *     prodCacheSeconds: 3600
 *     supportedLocales: zh_CN,en_US,zh_TW
 *     langParamName: lang
 *     i18nBaseNames: i18n/messages
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @see MessageSource
 * @see I18nProperties
 * @see WebI18nConfiguration
 */
@Slf4j
@AutoConfiguration(after = WebI18nConfiguration.class)
@EnableConfigurationProperties(I18nProperties.class)
@ConditionalOnClass(name = "org.springframework.context.MessageSource")
public class I18nConfiguration {

    private final I18nProperties properties;
    private final Environment environment;
    private final ObjectProvider<MessageSource> messageSourceProvider;

    public I18nConfiguration(I18nProperties properties,
                             ObjectProvider<Environment> environmentProvider,
                             ObjectProvider<MessageSource> messageSourceProvider) {
        this.properties = properties;
        this.environment = environmentProvider.getIfAvailable();
        this.messageSourceProvider = messageSourceProvider;
    }

    /**
     * 在 Bean 初始化完成后注入异常消息国际化解析器。
     *
     * <p>将 Spring {@link MessageSource} 桥接到 {@link AbstractYdszException}，
     * 使异常被抛出时只存储 i18n key + 参数，
     * 在 {@code getMessage()} 被调用时才懒加载解析为本地化消息。
     *
     * <p>无论 {@code MessageSource} 是由本类创建还是由外部提供，此方法都会执行注入。
     */
    @PostConstruct
    public void injectMessageResolver() {
        MessageSource messageSource = messageSourceProvider.getIfAvailable();
        if (messageSource == null) {
            log.warn("MessageSource 未找到，异常消息将降级为返回 i18n key");
            return;
        }
        AbstractYdszException.setMessageResolver(
                (key, params) -> messageSource.getMessage(key, params, key, LocaleContextHolder.getLocale())
        );
        log.info("异常消息国际化解析器已注入 | MessageSource: {}", messageSource.getClass().getSimpleName());
    }

    @Bean(name = "messageSource")
    @ConditionalOnMissingBean(name = "messageSource")
    public MessageSource messageSource() {
        boolean isProd = isProdEnvironment();
        int cacheSeconds = isProd ? properties.getProdCacheSeconds() : properties.getDevCacheSeconds();
        MessageSource messageSource = createMessageSource(cacheSeconds);
        // fail-fast：启动时校验所有已注册 ExceptionCode 的 i18n key 都能被 MessageSource 解析
        boolean validateEnabled = environment == null
                || environment.getProperty("ydsz.i18n.validate-on-startup", Boolean.class, true);
        if (validateEnabled) {
            validateExceptionCodeKeys(messageSource);
        }
        return messageSource;
    }

    /**
     * 启动时校验所有已注册的 ExceptionCode 的 i18n key 是否能在默认 messages.properties 中解析。
     *
     * <p>fail-fast 设计：如果存在缺失的 key，抛出 {@link IllegalStateException} 阻止应用启动，
     * 避免运行时出现 "No message found" 导致异常消息降级为 key 字符串。
     *
     * <p>校验范围：
     * <ul>
     *   <li>{@link UnifiedExceptionCode} 的所有 key（本模块必校）</li>
     *   <li>{@link ExceptionCodeRegistry#allRegistered()} 中其他模块注册的 ExceptionCode（如有）</li>
     * </ul>
     *
     * <p>可通过 {@code ydsz.i18n.validate-on-startup=false} 关闭校验（不推荐）。
     *
     * @param messageSource 已创建的 MessageSource 实例
     * @throws IllegalStateException 如果存在无法解析的 key
     */
    private void validateExceptionCodeKeys(MessageSource messageSource) {
        // 由于 MessageSource 配置了 useCodeAsDefaultMessage=true，
        // 未找到消息时返回 key 本身而非抛异常，因此通过比较返回值是否等于 key 来判断
        List<String> missingKeys = new ArrayList<>();

        // 1. 校验本模块 UnifiedExceptionCode 的所有 key
        for (UnifiedExceptionCode code : UnifiedExceptionCode.values()) {
            collectMissingKey(messageSource, code, missingKeys);
        }

        // 2. 校验其他模块通过 ExceptionCodeRegistry 注册的 ExceptionCode
        Map<String, ExceptionCode> registered = ExceptionCodeRegistry.allRegistered();
        for (Map.Entry<String, ExceptionCode> entry : registered.entrySet()) {
            ExceptionCode code = entry.getValue();
            // 跳过已通过 UnifiedExceptionCode 校验的（同一枚举实例）
            if (code instanceof UnifiedExceptionCode) {
                continue;
            }
            collectMissingKey(messageSource, code, missingKeys);
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

    /**
     * 校验单个 ExceptionCode 的 key 是否可解析，不可解析时加入 missingKeys 列表
     *
     * @param messageSource MessageSource 实例
     * @param code          待校验的 ExceptionCode
     * @param missingKeys   缺失 key 收集列表
     */
    private void collectMissingKey(MessageSource messageSource, ExceptionCode code, List<String> missingKeys) {
        String key = code.getKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            String message = messageSource.getMessage(key, null, null, Locale.ROOT);
            // useCodeAsDefaultMessage=true 时，未找到消息返回 key 本身
            if (message == null || message.equals(key)) {
                missingKeys.add(key + " (code=" + code.getCode() + ")");
            }
        } catch (Exception e) {
            missingKeys.add(key + " (code=" + code.getCode() + ", error=" + e.getMessage() + ")");
        }
    }

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

        String basename = properties.getBasename();
        String[] basenameArray = basename.split(",");
        List<String> basenameList = new ArrayList<>();
        for (String bn : basenameArray) {
            basenameList.add(bn.trim());
        }
        String i18nBaseNames = properties.getI18nBaseNames();
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

        messageSource.setDefaultEncoding(properties.getEncoding());
        messageSource.setCacheSeconds(cacheSeconds);
        messageSource.setFallbackToSystemLocale(properties.isFallbackToSystemLocale());
        messageSource.setUseCodeAsDefaultMessage(true);

        validateAndLogConfig(messageSource, cacheSeconds);

        return messageSource;
    }

    private void validateAndLogConfig(MessageSource messageSource, int cacheSeconds) {
        try {
            messageSource.getMessage("test.key", null, Locale.CHINA);
            log.info("国际化配置加载成功 | 基础路径: {} | 缓存时间: {}秒 | 支持语言: {}",
                    properties.getBasename(), cacheSeconds, Arrays.toString(properties.getSupportedLocales()));
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("No message found under key 'test.key'")) {
                log.warn("国际化配置文件加载成功，但未找到测试key: test.key（非必选）");
            } else {
                log.error("国际化配置加载检查异常", e);
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(Validator.class)
    public Validator getValidator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        log.info("验证器已关联国际化消息源");
        return validator;
    }
}
