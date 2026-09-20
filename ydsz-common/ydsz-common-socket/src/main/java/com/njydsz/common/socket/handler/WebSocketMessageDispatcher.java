package com.njydsz.common.socket.handler;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

import com.njydsz.common.socket.serialize.MessageSerializer;
import com.njydsz.common.socket.session.WebSocketSessionAttributes;

/**
 * WebSocket 消息分发器（FEAT-002）。
 *
 * <p>在单例 Bean 初始化阶段扫描所有 Spring Bean 中标注了 {@link WebSocketHandler} 的方法，
 * 构建 action → 处理器映射，并在 STOMP SEND 帧到达时根据请求头 {@code ws-action} 匹配并调用
 * 该处理器。
 *
 * <p>处理过程：
 *
 * <ol>
 *   <li>客户端 STOMP SEND 帧 → {@code StompMessageInterceptor.handleSend()} 提取 action header
 *   <li>调用 {@link #dispatch(String, String, Map)} 做反射调用
 *       <ul>
 *         <li>单参数签名：传入反序列化后的消息体
 *         <li>双参数签名：先传入反序列化消息体，再传入 {@link WebSocketSessionAttributes}（框架注入的 Session 属性视图）
 *       </ul>
 * </ol>
 *
 * <p>重复的 action 在启动阶段以 fail-fast 抛出 IllegalStateException，便于接入方尽早发现路由冲突。
 *
 * <p>线程安全：{@link #dispatch} 方法是线程安全的；底层 handlerMap 是不可变映射，初始化后只读。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
@Slf4j
public class WebSocketMessageDispatcher implements SmartInitializingSingleton {

  /** WebSocket 自定义头：动作路由键 */
  public static final String ACTION_HEADER = "ws-action";

  private final ApplicationContext applicationContext;
  private final MessageSerializer messageSerializer;

  /** action → HandlerMethod 映射 */
  private volatile Map<String, HandlerMethod> handlerMap = new ConcurrentHashMap<>();

  /**
   * 构造消息分发器。
   *
   * @param applicationContext Spring 应用上下文，用于扫描 Bean
   * @param messageSerializer 消息序列化器，用于反序列化 payload
   */
  public WebSocketMessageDispatcher(
      ApplicationContext applicationContext, MessageSerializer messageSerializer) {
    this.applicationContext = applicationContext;
    this.messageSerializer = messageSerializer;
  }

  /**
   * 在所有单例 Bean 初始化完成后扫描所有标注了 @WebSocketHandler 的方法并构建路由映射。
   *
   * <p>重复 action 在启动时以 fail-fast 抛出异常，避免上线后的消息误路由。
   */
  @Override
  public void afterSingletonsInstantiated() {
    Map<String, HandlerMethod> newHandlers = new ConcurrentHashMap<>();
    String[] beanNames = applicationContext.getBeanDefinitionNames();
    for (String beanName : beanNames) {
      try {
        Object bean = applicationContext.getBean(beanName);
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        for (Method method : targetClass.getDeclaredMethods()) {
          WebSocketHandler annotation = method.getAnnotation(WebSocketHandler.class);
          if (annotation != null) {
            registerHandlerMethod(annotation, bean, method, newHandlers);
          }
        }
      } catch (Exception e) {
        log.debug("[WS-Dispatcher] 跳过 bean {}: {}", beanName, e.getMessage());
      }
    }
    handlerMap = newHandlers;
    if (!handlerMap.isEmpty()) {
      log.info("[WS-Dispatcher] C→S 消息路由注册完成, actionCount={}", handlerMap.size());
    } else {
      log.warn("[WS-Dispatcher] 未发现任何 @WebSocketHandler 注册，C→S 消息将无法路由");
    }
  }

  /**
   * 分发消息到对应的处理器。
   *
   * <p>当 STOMP SEND 帧携带的 ws-action 头无法匹配任何 {@link WebSocketHandler#action()} 时返回
   * false（即"未分发"），由调用方决定是丢弃还是作通用处理。分发过程中若目标 Bean 不是 Spring
   * 单例 / 方法反射异常，均降级记录 warn 日志并返回 true（已分发但处理异常，不反复重试）。
   *
   * @param action STOMP SEND 帧的 "ws-action" 头
   * @param payloadJson SEND 帧体（JSON 序列化字符串）
   * @param sessionAttributes 当前 Session 属性（用于注入 WebSocketSessionAttributes 参数）
   * @return true 表示已找到匹配的处理器并执行（或处理器执行异常），false 表示 action 未注册
   */
  public boolean dispatch(String action, String payloadJson, Map<String, Object> sessionAttributes) {
    if (action == null || action.isEmpty()) {
      return false;
    }
    HandlerMethod handler = handlerMap.get(action);
    if (handler == null) {
      return false;
    }
    try {
      invokeHandler(handler, payloadJson, sessionAttributes);
      return true;
    } catch (Exception e) {
      log.warn(
          "[WS-Dispatcher] 处理器执行异常: action={}, err={}",
          action,
          e.getMessage());
      return true;
    }
  }

  /**
   * 返回当前已注册的处理器数量（供健康指标和运维诊断）。
   *
   * @return 注册数
   */
  public int getHandlerCount() {
    return handlerMap.size();
  }

  // ==================== 内部方法 ====================

  /**
   * 注册一个 @WebSocketHandler 方法到映射表。
   *
   * @param annotation 注解实例
   * @param bean Bean 实例（用于反射调用）
   * @param method 方法对象
   * @param targetMap 注册表
   */
  private void registerHandlerMethod(
      WebSocketHandler annotation, Object bean, Method method, Map<String, HandlerMethod> targetMap) {
    String action = annotation.action();
    if (action == null || action.trim().isEmpty()) {
      log.warn("[WS-Dispatcher] @WebSocketHandler 方法缺少 action, 跳过: {}", method);
      return;
    }
    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length == 0 || paramTypes.length > 2) {
      throw new IllegalStateException(
          String.format(
              "@WebSocketHandler 方法参数数量应为 1~2, 实际: %d, method=%s",
              paramTypes.length, method));
    }
    if (paramTypes.length == 2 && !WebSocketSessionAttributes.class.isAssignableFrom(paramTypes[1])) {
      throw new IllegalStateException(
          String.format(
              "@WebSocketHandler 双参数签名中第二参数必须为 WebSocketSessionAttributes, method=%s",
              method));
    }
    HandlerMethod handlerMethod = new HandlerMethod(bean, method, paramTypes[0], paramTypes.length == 2);
    HandlerMethod previous = targetMap.putIfAbsent(action, handlerMethod);
    if (previous != null) {
      throw new IllegalStateException(
          String.format("@WebSocketHandler action 重复注册: action=%s", action));
    }
    log.info(
        "[WS-Dispatcher] 注册 C→S 处理器: action={}, method={}",
        action,
        method.getDeclaringClass().getSimpleName() + "." + method.getName());
  }

  /**
   * 反射调用处理器方法，根据参数数量传入 payload 及可选的 sessionAttributes。
   *
   * @param handler 处理器描述
   * @param payloadJson payload JSON
   * @param sessionAttributes Session 属性
   */
  private void invokeHandler(
      HandlerMethod handler, String payloadJson, Map<String, Object> sessionAttributes) throws Exception {
    Object payload = messageSerializer.deserialize(payloadJson, handler.payloadType);
    if (handler.isHasSessionAttributesParam) {
      handler.method.invoke(handler.bean, payload, WebSocketSessionAttributes.of(sessionAttributes));
    } else {
      handler.method.invoke(handler.bean, payload);
    }
  }

  /**
   * 处理器描述（不可变记录）。
   *
   * @param bean Spring Bean 实例（用于反射调用 method）
   * @param method 处理方法
   * @param payloadType payload 反序列化目标类型
   * @param isHasSessionAttributesParam 是否声明 WebSocketSessionAttributes 参数
   */
  private record HandlerMethod(
      Object bean, Method method, Class<?> payloadType, boolean isHasSessionAttributesParam) {}
}
