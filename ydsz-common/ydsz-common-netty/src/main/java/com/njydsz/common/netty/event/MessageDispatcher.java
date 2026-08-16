package com.njydsz.common.netty.event;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * 消息分发器 — 基于 {@link MessageHandler} 注解自动路由消息到处理方法。
 *
 * <p>扫描 Spring 容器中所有带有 {@code @MessageHandler} 注解方法的 Bean，
 * 在运行时根据消息中的 {@code type} 字段路由到对应的处理方法。
 *
 * <p>协议格式：JSON 消息需包含 {@code type} 字段用于路由。
 *
 * <p>使用方式：
 * <ol>
 *   <li>在业务 Bean 方法上标注 {@code @MessageHandler(type = "XXX")}</li>
 *   <li>将 {@code MessageDispatcher} 添加到 Netty Pipeline</li>
 * </ol>
 *
 * <p>方法签名要求：{@code void method(ChannelHandlerContext ctx, Map<String, Object> data)}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MessageDispatcher extends ChannelInboundHandlerAdapter {

    /** type -> HandlerMethod 映射 */
    private final Map<String, HandlerMethod> handlerMap = new ConcurrentHashMap<>();

    /** 已扫描标志 */
    private volatile boolean initialized = false;

    private final ApplicationContext applicationContext;

    /**
     * 构造消息分发器。
     *
     * @param applicationContext Spring 应用上下文（用于扫描 @MessageHandler 注解）
     */
    public MessageDispatcher(@Autowired(required = false) ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 初始化扫描 — 懒加载，首次消息到达时触发。
     */
    private void initIfNeeded() {
        if (initialized || applicationContext == null) {
            initialized = true;
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
            for (Map.Entry<String, Object> entry : beans.entrySet()) {
                Object bean = entry.getValue();
                Class<?> clazz = bean.getClass();
                for (Method method : clazz.getDeclaredMethods()) {
                    MessageHandler annotation = AnnotationUtils.findAnnotation(method, MessageHandler.class);
                    if (annotation != null) {
                        String type = annotation.type();
                        if (handlerMap.containsKey(type)) {
                            log.warn("[Netty-Dispatcher] 消息类型 {} 已有处理器，将被覆盖", type);
                        }
                        method.setAccessible(true);
                        handlerMap.put(type, new HandlerMethod(bean, method));
                        log.debug("[Netty-Dispatcher] 注册消息处理器: type={}, bean={}, method={}",
                                type, entry.getKey(), method.getName());
                    }
                }
            }
            log.info("[Netty-Dispatcher] 初始化完成, 已注册 {} 个消息处理器", handlerMap.size());
            initialized = true;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof ByteBuf buf)) {
            super.channelRead(ctx, msg);
            return;
        }

        initIfNeeded();

        String json = buf.toString(CharsetUtil.UTF_8);
        Map<String, Object> data;
        try {
            data = YdszJson.parseMap(json);
        } catch (Exception e) {
            log.warn("[Netty-Dispatcher] 消息解析失败: {}", e.getMessage());
            super.channelRead(ctx, msg);
            return;
        }

        String type = (String) data.get("type");
        if (type == null) {
            super.channelRead(ctx, msg);
            return;
        }

        HandlerMethod handler = handlerMap.get(type);
        if (handler == null) {
            log.debug("[Netty-Dispatcher] 未找到消息类型 {} 的处理器", type);
            super.channelRead(ctx, msg);
            return;
        }

        try {
            handler.method.invoke(handler.bean, ctx, data);
        } catch (Exception e) {
            log.error("[Netty-Dispatcher] 消息处理异常: type={}, err={}",
                    type, e.getMessage(), e);
        }
    }

    /**
     * 获取已注册的消息类型列表。
     *
     * @return 消息类型集合
     */
    public Set<String> getRegisteredTypes() {
        return Set.copyOf(handlerMap.keySet());
    }

    /** 处理器方法元数据 */
    private record HandlerMethod(Object bean, Method method) {
    }
}
