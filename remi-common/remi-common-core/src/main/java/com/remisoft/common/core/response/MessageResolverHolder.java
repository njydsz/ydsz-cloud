package com.remisoft.common.core.response;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 国际化消息解析器持有者。
 *
 * <p>将 {@link BaseResponse} 的国际化逻辑独立封装，实现职责分离。
 * 采用一次性设置语义：启动时由框架注入，后续不可修改。
 *
 * <p><b>设计动机：</b>
 * <ul>
 *   <li>消除 BaseResponse 中的静态可变状态，使响应类更加纯量</li>
 *   <li>国际化逻辑可独立演进和测试</li>
 *   <li>为未来国际化策略的扩展（如多 MessageSource 支持）提供独立演化空间</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 在自动配置中注册
 * MessageResolverHolder.setResolverIfAbsent(new SpringMessageResolver(messageSource));
 *
 * // 在需要解析消息处调用
 * String message = MessageResolverHolder.resolve("error.BAD_REQUEST", "参数错误");
 * }</pre>
 *
 * @author remi-team
 * @since 1.8.0
 * @see BaseResponse.MessageResolver
 */
public final class MessageResolverHolder {

    /**
     * 国际化消息解析器接口。
     *
     * <p>从 BaseResponse 中迁出，定义标准化的消息解析契约。
     */
    @FunctionalInterface
    public interface MessageResolver {
        /**
         * 解析国际化消息
         *
         * @param key          国际化消息 key
         * @param defaultValue 默认消息文本
         * @return 解析后的消息内容
         */
        String resolve(String key, String defaultValue);
    }

    /**
     * 解析器实例（AtomicReference 保证线程安全和一次性设置）。
     */
    private static final AtomicReference<MessageResolver> RESOLVER = new AtomicReference<>();

    private MessageResolverHolder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 一次性设置全局消息解析器（仅首次调用生效）。
     *
     * <p>由自动配置类在应用启动时调用。
     * 由于采用一次性设置语义，重复调用不会覆盖已有解析器。
     *
     * @param resolver 消息解析器实现
     * @return true=设置成功（首次），false=已存在解析器（忽略）
     */
    public static boolean setResolverIfAbsent(MessageResolver resolver) {
        boolean success = RESOLVER.compareAndSet(null, resolver);
        if (!success && resolver != null) {
            org.slf4j.LoggerFactory.getLogger(MessageResolverHolder.class)
                    .debug("MessageResolver already registered, ignoring subsequent setResolverIfAbsent call");
        }
        return success;
    }

    /**
     * 检查国际化消息解析器是否已注册
     *
     * @return 已注册返回 true，否则返回 false
     */
    public static boolean isResolverRegistered() {
        return RESOLVER.get() != null;
    }

    /**
     * 解析国际化消息，若未设置解析器则返回默认值。
     *
     * <p>包级可见，仅供 response 包内的类使用。
     *
     * @param key          国际化消息 key
     * @param defaultValue 默认消息文本
     * @return 解析后的消息内容
     */
    static String resolveMessage(String key, String defaultValue) {
        MessageResolver currentResolver = RESOLVER.get();
        if (currentResolver != null) {
            String result = currentResolver.resolve(key, defaultValue);
            return result != null ? result : defaultValue;
        }
        return defaultValue;
    }

    /**
     * 测试辅助方法：清空已注册的消息解析器（仅用于单元测试）。
     *
     * <p><b>仅限测试使用。</b>
     */
    static void __testResetResolver() {
        RESOLVER.set(null);
    }
}
