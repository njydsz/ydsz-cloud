package com.njydsz.common.queue.handler;

import java.lang.reflect.Method;
import java.util.Map;

import com.njydsz.common.queue.domain.QueueMessage;
import com.njydsz.common.queue.service.IMessageHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * 方法调用消息处理器
 *
 * <p>包装 {@link java.lang.reflect.Method} 为 {@link IMessageHandler}，
 * 在消息到达时通过反射调用目标方法。支持不同参数类型的自动适配。
 *
 * <p><b>支持的参数类型：</b>
 * <ul>
 *   <li>{@link QueueMessage} - 完整消息对象</li>
 *   <li>{@code String} - 消息体字符串</li>
 *   <li>{@code Map<String, Object>} - 消息体反序列化后的 Map</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MethodMessageHandler implements IMessageHandler {

    private final Object bean;
    private final Method method;
    private final boolean ignoreExceptions;

    /**
     * 创建方法调用处理器
     *
     * @param bean              目标 Bean 实例
     * @param method            目标方法
     * @param ignoreExceptions  是否忽略异常（不触发重试）
     */
    public MethodMessageHandler(Object bean, Method method, boolean ignoreExceptions) {
        this.bean = bean;
        this.method = method;
        this.ignoreExceptions = ignoreExceptions;
    }

    @Override
    public void onMessage(QueueMessage message) throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length == 0) {
            // 无参数方法，直接调用
            method.invoke(bean);
            return;
        }

        // 根据参数类型适配
        Class<?> paramType = paramTypes[0];
        if (paramType == QueueMessage.class) {
            method.invoke(bean, message);
        } else if (paramType == String.class) {
            String body = message != null ? message.getBody() : null;
            method.invoke(bean, body);
        } else if (paramType == Map.class) {
            // 尝试将 body 反序列化为 Map
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = message != null && message.getBody() != null
                    ? new com.njydsz.common.json.YdszJson().fromJsonToMap(message.getBody())
                    : null;
            method.invoke(bean, bodyMap);
        } else {
            // 未知参数类型，尝试传入原始消息
            log.warn("[MethodHandler] 未知参数类型: {}, 尝试传入 QueueMessage", paramType.getName());
            method.invoke(bean, message);
        }
    }
}
