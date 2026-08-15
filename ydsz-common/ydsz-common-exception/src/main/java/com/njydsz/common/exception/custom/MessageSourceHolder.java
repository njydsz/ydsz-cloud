package com.njydsz.common.exception.custom;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化消息源静态持有者
 *
 * <p>为 {@link AbstractYdszException} 提供无侵入的 i18n 消息解析能力。
 * 由 {@code YdszExceptionCoreAutoConfiguration} 在启动时注入 Spring {@code MessageSource}，
 * 使异常类在不直接依赖 Spring 上下文的情况下实现 i18n 消息懒加载解析。
 *
 * <p><b>线程安全：</b>使用 volatile 引用 + 空检查，保证多线程可见性。
 * 一旦注入完成（应用就绪后），仅读取不写入，无并发风险。
 *
 * <p><b>使用约束：</b>
 * <ul>
 *     <li>未注入 MessageSource 时（如单元测试、非 Spring 环境），{@link #resolve} 直接返回 key</li>
 *     <li>注入后若 key 在 MessageSource 中不存在，返回 key 本身（兜底）</li>
 *     <li>解析 Locale 默认取当前请求线程绑定的 Locale（{@link LocaleContextHolder}），
 *         无 Web 上下文时回退到 {@link Locale#ROOT}，保证多语言切换真实生效</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.3.0
 */
public final class MessageSourceHolder {

    private static final Logger log = LoggerFactory.getLogger(MessageSourceHolder.class);

    private MessageSourceHolder() {
        // 工具类，禁止实例化
    }

    /**
     * 内部函数式接口，模拟 MessageSource.getMessage(key, params, defaultMsg, Locale) 签名。
     * 使用内部接口而非直接持有 MessageSource 引用，避免模块对 Spring 的编译期依赖。
     */
    @FunctionalInterface
    public interface MessageResolver {
        /**
         * 解析国际化消息
         *
         * @param key          消息键
         * @param params       消息参数（可为 null）
         * @param defaultMsg   默认消息
         * @param locale       区域设置
         * @return 解析后的消息
         */
        String resolve(String key, Object[] params, String defaultMsg, Locale locale);
    }

    private static volatile MessageResolver resolver;

    /**
     * 注入消息解析器（由 AutoConfiguration 在启动时调用一次）
     *
     * @param newResolver 消息解析器，为 null 则清除当前解析器
     */
    public static void setResolver(MessageResolver newResolver) {
        resolver = newResolver;
        if (newResolver != null) {
            log.debug("MessageSourceResolver 已注入，AbstractYdszException 的 getMessage() 将启用 i18n 解析");
        }
    }

    /**
     * 检查解析器是否已注入
     *
     * @return 已注入返回 true
     */
    public static boolean isAvailable() {
        return resolver != null;
    }

    /**
     * 获取当前请求线程绑定的 Locale。
     *
     * <p>Web 请求场景返回 {@link LocaleContextHolder} 绑定的请求 Locale；
     * 无请求上下文（定时任务、MQ 消费等）时返回系统默认 Locale，永不为 null。
     *
     * @return 当前解析使用的 Locale，永不为 null
     */
    public static Locale currentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null ? locale : Locale.ROOT;
    }

    /**
     * 解析国际化消息（供 AbstractYdszException.getMessage() 调用）。
     *
     * <p>按当前请求线程的 Locale 解析，保证同一异常在不同语言请求下返回对应文案。
     * 若解析器未注入，直接返回 messageKey 本身（保持向后兼容）。
     * 若解析器已注入但解析失败（如 key 不存在），同样返回 messageKey 兜底。
     *
     * @param messageKey    消息键
     * @param messageParams 消息参数
     * @return 已解析的国际化消息；解析失败时返回 messageKey
     */
    public static String resolve(String messageKey, Object[] messageParams) {
        return resolve(messageKey, messageParams, currentLocale());
    }

    /**
     * 按指定 Locale 解析国际化消息（供显式指定语言场景使用）。
     *
     * @param messageKey    消息键
     * @param messageParams 消息参数
     * @param locale        Locale，为 null 时回退到 {@link Locale#ROOT}
     * @return 已解析的国际化消息；解析失败时返回 messageKey
     */
    public static String resolve(String messageKey, Object[] messageParams, Locale locale) {
        MessageResolver r = resolver;
        if (r == null || messageKey == null) {
            return messageKey;
        }
        Locale resolvedLocale = locale != null ? locale : Locale.ROOT;
        try {
            String resolved = r.resolve(messageKey, messageParams, messageKey, resolvedLocale);
            return resolved != null ? resolved : messageKey;
        } catch (Exception e) {
            // 解析失败时兜底返回 key，避免异常信息丢失
            return messageKey;
        }
    }
}
