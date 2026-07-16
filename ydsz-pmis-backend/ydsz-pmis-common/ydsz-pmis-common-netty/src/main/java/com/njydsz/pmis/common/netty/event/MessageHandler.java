package com.njydsz.pmis.common.netty.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消息处理器注解 — 标记方法为特定消息类型的处理器。
 *
 * <p>类似 Spring MVC 的 {@code @RequestMapping}，但用于 Netty 消息分发。
 * 被 {@link MessageDispatcher} 扫描并注册。
 *
 * <p>使用方式：
 * <pre>{@code
 * @Component
 * public class MyMessageHandler {
 *
 *     @MessageHandler(type = "AUTH")
 *     public void handleAuth(ChannelHandlerContext ctx, Map<String, Object> data) {
 *         String userId = (String) data.get("userId");
 *         // 处理认证消息...
 *     }
 *
 *     @MessageHandler(type = "PING")
 *     public void handlePing(ChannelHandlerContext ctx, Map<String, Object> data) {
 *         // 处理心跳消息...
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageHandler {

    /**
     * 消息类型（用于匹配消息中的 type 字段）。
     *
     * @return 消息类型字符串
     */
    String type();

}
