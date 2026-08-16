package com.njydsz.common.base.i18n;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 国际化消息解析器注册表。
 *
 * <p>取代纯静态持有器的 Spring 友好实现，支持：
 * <ul>
 *   <li>Spring 依赖注入（{@link org.springframework.beans.factory.annotation.Autowired}）</li>
 *   <li>程序化注册（向后兼容 {@link MessageResolverHolder}）</li>
 *   <li>默认降级解析器（未注入时使用 {@link MessageResolverHolder} 的值）</li>
 * </ul>
 *
 * <p>测试时可直接调用 {@link #reset()} 重置状态，无需反射。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class MessageResolverRegistry {

    private static final Logger log = LoggerFactory.getLogger(MessageResolverRegistry.class);

    private final AtomicReference<MessageResolverHolder.MessageResolver> primaryResolver =
            new AtomicReference<>();

    /**
     * 注册消息解析器。
     *
     * <p>允许后续注册覆盖（不同于 setResolverIfAbsent），
     * 便于测试和动态替换。
     *
     * @param resolver 消息解析器
     */
    public void register(MessageResolverHolder.MessageResolver resolver) {
        MessageResolverHolder.MessageResolver previous = primaryResolver.getAndSet(resolver);
        if (previous != null) {
            log.debug("MessageResolver replaced");
        }
    }

    /**
     * 一次性注册消息解析器（仅当尚未注册时生效）。
     *
     * @param resolver 消息解析器
     * @return true 表示本次注册成功
     */
    public boolean registerIfAbsent(MessageResolverHolder.MessageResolver resolver) {
        return primaryResolver.compareAndSet(null, resolver);
    }

    /**
     * 解析国际化消息。
     *
     * <p>优先使用 Spring 注入的解析器，
     * 未注入时降级使用 MessageResolverHolder 中的值。
     *
     * @param key          消息 key
     * @param defaultValue 解析失败时的默认值
     * @return 解析后的消息
     */
    public String resolve(String key, String defaultValue) {
        MessageResolverHolder.MessageResolver resolver = primaryResolver.get();
        if (resolver != null) {
            String result = resolver.resolveMessage(key, defaultValue);
            return result != null ? result : defaultValue;
        }
        // 降级到静态持有器
        return MessageResolverHolder.resolveMessage(key, defaultValue);
    }

    /**
     * 测试专用：重置已注册的解析器。
     */
    public void reset() {
        primaryResolver.set(null);
    }
}
