package com.njydsz.pmis.common.exception.i18n;

import java.util.Locale;

/**
 * 消息解析器持有器
 *
 * <p>桥接 {@code common-core}（无 Spring 依赖）与 {@code common-web}（有 MessageSource），
 * 使异常类可以在不依赖 Spring 的情况下实现 i18n 消息懒加载解析。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>{@code common-web} 的 {@code I18nConfig} 启动时调用 {@link #setResolver(MessageResolver)}
 *       注入基于 Spring {@code MessageSource} 的解析器</li>
 *   <li>异常被抛出时只存储 i18n key + 参数，不立即解析</li>
 *   <li>当 {@code getMessage()} 被调用时（如日志记录、响应序列化），才通过此持有器
 *       获取解析器并解析消息</li>
 * </ol>
 *
 * <p>避免在异常构造时进行 i18n 解析，提升异常创建性能（尤其在高频校验场景）。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class MessageResolverHolder {

    private static volatile MessageResolver resolver;

    private MessageResolverHolder() {
    }

    /**
     * 设置消息解析器（由 Spring 容器启动时调用）
     *
     * @param resolver 消息解析器
     */
    public static void setResolver(MessageResolver resolver) {
        MessageResolverHolder.resolver = resolver;
    }

    /**
     * 获取消息解析器
     *
     * @return 消息解析器；未注册时返回 null
     */
    public static MessageResolver getResolver() {
        return resolver;
    }

    /**
     * 解析 i18n 消息
     *
     * @param key    消息键
     * @param args   占位符参数
     * @param locale 语言环境（null 时使用系统默认）
     * @return 解析后的消息；解析器未注册时返回 key 本身
     */
    public static String resolve(String key, Object[] args, Locale locale) {
        if (resolver == null || key == null) {
            return key;
        }
        try {
            return resolver.resolve(key, args, locale);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 解析 i18n 消息（使用系统默认 Locale）
     *
     * @param key  消息键
     * @param args 占位符参数
     * @return 解析后的消息；解析器未注册时返回 key 本身
     */
    public static String resolve(String key, Object[] args) {
        return resolve(key, args, null);
    }

    /**
     * 清除解析器（用于测试清理）
     */
    public static void clear() {
        resolver = null;
    }
}
