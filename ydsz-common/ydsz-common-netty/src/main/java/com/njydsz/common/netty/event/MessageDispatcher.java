package com.njydsz.common.netty.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import com.njydsz.common.json.YdszJson;

/**
 * 消息分发器 — 基于 {@link MessageHandler} 注解自动路由消息到处理方法。
 *
 * <p>扫描 Spring 容器中所有带有 {@code @MessageHandler} 注解方法的 Bean，
 * 在运行时根据消息中的 {@code type} 字段路由到对应的处理方法。
 *
 * <p>核心优化：使用 {@link MethodHandle} 替代反射 {@link Method#invoke}，
 * 经 JIT 预热后接近直接调用性能（比 {@code Method.invoke} 快 3-5 倍）。
 *
 * <p>协议格式：JSON 消息需包含 {@code type} 字段用于路由。
 *
 * <p>使用方式（注解扫描）：
 * <ol>
 *   <li>在业务 Bean 方法上标注 {@code @MessageHandler(type = "XXX")}</li>
 *   <li>将 {@code MessageDispatcher} 添加到 Netty Pipeline</li>
 * </ol>
 *
 * <p>使用方式（编程式注册，性能更优）：
 * <pre>{@code
 * dispatcher.register("ORDER_CREATE", bean, "handleOrderCreate");
 * }</pre>
 *
 * <p>方法签名要求：{@code void method(ChannelHandlerContext ctx, Map&lt;String, Object&gt; data)}
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 v1.1.0 起标记废弃，无活跃消费者。推荐使用
 *             {@code SimpleChannelInboundHandler<T>} + switch 策略模式替代。
 *             计划在 v2.0.0 移除，请使用
 *             <a href="https://github.com/njydsz/ydsz-cloud/blob/main/ydsz-common/ydsz-common-netty/README.md">README</a>
 *             推荐的 {@code SimpleChannelInboundHandler} 模式。
 */
@Deprecated(since = "1.1.0", forRemoval = true)
@Slf4j
public class MessageDispatcher extends ChannelInboundHandlerAdapter {

    /** type -> HandlerMethod 映射 */
    private final Map<String, HandlerMethod> handlerMap = new ConcurrentHashMap<>();

    /** 已扫描标志 */
    private volatile boolean initialized = false;

    /** MethodHandles lookup 用于创建 MethodHandle */
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    /** 目标方法签名类型 */
    private static final MethodType TARGET_TYPE = MethodType.methodType(
            void.class, ChannelHandlerContext.class, Map.class);

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
     * 注册消息处理器（编程式 API，性能优于注解扫描）。
     *
     * @param type   消息类型标识
     * @param bean   处理器 Bean 实例
     * @param method 处理器方法名
     * @return 当前实例（链式调用）
     */
    public MessageDispatcher register(String type, Object bean, String method) {
        try {
            Method targetMethod = bean.getClass().getDeclaredMethod(
                    method, ChannelHandlerContext.class, Map.class);
            targetMethod.setAccessible(true);
            MethodHandle handle = LOOKUP.unreflect(targetMethod).bindTo(bean);
            handlerMap.put(type, new HandlerMethod(bean, handle));
            log.debug("[Netty-Dispatcher] 注册消息处理器(编程式): type={}, bean={}, method={}",
                    type, bean.getClass().getSimpleName(), method);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalArgumentException("注册消息处理器失败: type=" + type
                    + ", method=" + method + ", err=" + e.getMessage(), e);
        }
        return this;
    }

    /**
     * 移除已注册的消息处理器。
     *
     * @param type 消息类型标识
     * @return 当前实例（链式调用）
     */
    public MessageDispatcher unregister(String type) {
        handlerMap.remove(type);
        return this;
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
            scanAnnotationHandlers();
            log.info("[Netty-Dispatcher] 注解扫描完成, 已注册 {} 个消息处理器", handlerMap.size());
            initialized = true;
        }
    }

    /**
     * 扫描 Spring 容器中所有 Bean 的 {@link MessageHandler} 注解。
     */
    private void scanAnnotationHandlers() {
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();
            for (Method method : clazz.getDeclaredMethods()) {
                MessageHandler annotation = AnnotationUtils.findAnnotation(method, MessageHandler.class);
                if (annotation != null) {
                    registerHandlerMethod(annotation.type(), bean, method);
                }
            }
        }
    }

    /**
     * 注解扫描时注册处理器方法。
     *
     * @param type   消息类型
     * @param bean   处理器 Bean
     * @param method 目标方法
     */
    private void registerHandlerMethod(String type, Object bean, Method method) {
        if (handlerMap.containsKey(type)) {
            log.warn("[Netty-Dispatcher] 消息类型 {} 已有处理器，将被覆盖", type);
        }
        try {
            method.setAccessible(true);
            MethodHandle handle = LOOKUP.unreflect(method).bindTo(bean);
            handlerMap.put(type, new HandlerMethod(bean, handle));
            log.debug("[Netty-Dispatcher] 注册消息处理器(注解): type={}, bean={}, method={}",
                    type, bean.getClass().getSimpleName(), method.getName());
        } catch (IllegalAccessException e) {
            log.warn("[Netty-Dispatcher] 无法创建 MethodHandle: type={}, method={}",
                    type, method.getName(), e);
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
            handler.handle.invoke(ctx, data);
        } catch (Throwable e) {
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

    /**
     * 获取已注册的处理器数量。
     *
     * @return 处理器数量
     */
    public int getHandlerCount() {
        return handlerMap.size();
    }

    /** 处理器方法元数据（使用 MethodHandle 提升调用性能） */
    private record HandlerMethod(Object bean, MethodHandle handle) {
    }
}
