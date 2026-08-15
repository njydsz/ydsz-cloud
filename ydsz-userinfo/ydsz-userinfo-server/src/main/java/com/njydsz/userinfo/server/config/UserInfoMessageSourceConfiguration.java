package com.njydsz.userinfo.server.config;

import java.util.Arrays;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户中心模块 MessageSource 扩展配置
 *
 * <p>P0-3: 补齐登录失败统一返回（消除用户名枚举）的 i18n 消息加载。
 *
 * <p>ydsz-common 的 {@code I18nAutoConfiguration} 仅加载了通用 basename
 * （{@code i18n/messages}、{@code i18n/core/messages}、{@code i18n/file-messages}），
 * 未覆盖 userinfo 模块的异常码 i18n key（如 {@code userinfo.password.incorrect}）。
 *
 * <p>本配置在 Spring Boot 应用上下文中声明一个扩展基名的 {@link MessageSource}，
 * 将原 common 基名与 userinfo 模块的 {@code i18n/userinfo-messages} 合并加载，
 * 确保 {@code ExceptionCodeScanner} 启动校验通过且异常 {@code getMessage()} 返回
 * 正确的国际化文案（而非裸 key）。
 *
 * <p><b>基名加载顺序：</b>
 * <ol>
 *   <li>{@code classpath:i18n/messages} — 通用 base 消息（common-base）</li>
 *   <li>{@code classpath:i18n/core/messages} — 核心错误码（common-core）</li>
 *   <li>{@code classpath:i18n/file-messages} — 文件存储（common-file）</li>
 *   <li>{@code classpath:i18n/userinfo-messages} — 用户中心模块消息</li>
 * </ol>
 *
 * <p>{@code @ConditionalOnMissingBean} 保证与 ydsz-common 的自动装配互不冲突：
 * 若上下文中已存在其他 {@link MessageSource} Bean，本配置不生效。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(MessageSource.class)
@ConditionalOnMissingBean(MessageSource.class)
public class UserInfoMessageSourceConfiguration {

    /** 通用基名（与 ydsz-common I18nAutoConfiguration 保持一致） */
    private static final String[] COMMON_BASENAMES = {
            "classpath:i18n/messages",
            "classpath:i18n/core/messages",
            "classpath:i18n/file-messages"
    };

    /** 用户中心模块专属 i18n 基名 */
    private static final String USERINFO_BASENAME = "classpath:i18n/userinfo-messages";

    /**
     * 创建扩展基名的国际化消息源。
     *
     * <p>将 common 模块基名与 userinfo 模块基名合并，
     * 使 {@code ExceptionCodeScanner} 能校验全量异常码 key，
     * 同时保证 {@code AbstractYdszException.getMessage()} 返回正确的用户可读文案。
     *
     * @return 合并基名的 MessageSource 实例
     */
    @Bean
    public MessageSource messageSource() {
        int totalLength = COMMON_BASENAMES.length + 1;
        String[] allBasenames = new String[totalLength];
        System.arraycopy(COMMON_BASENAMES, 0, allBasenames, 0, COMMON_BASENAMES.length);
        allBasenames[COMMON_BASENAMES.length] = USERINFO_BASENAME;

        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasenames(allBasenames);
        source.setDefaultEncoding("UTF-8");
        source.setCacheSeconds(5);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);

        log.info("[UserInfoMessageSource] 用户中心 i18n 基名已注册，basenames={}",
                Arrays.toString(allBasenames));
        return source;
    }
}
