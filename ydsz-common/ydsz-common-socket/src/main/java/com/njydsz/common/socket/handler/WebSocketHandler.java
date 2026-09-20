package com.njydsz.common.socket.handler;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * WebSocket 消息处理器注解（FEAT-002）。
 *
 * <p>标记一个 Spring Bean 方法为 Client-to-Server 消息处理器，框架在应用启动时自动扫描注册
 * 方法到 {@code WebSocketMessageDispatcher}。客户端通过 STOMP SEND 帧发送的消息经过框架路由，
 * 匹配到方法级别的 {@code action} 后调用对应处理器。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>被注解方法必须声明在一个 Spring 管理的 Bean 中（{@link Component} 或派生注解）
 *   <li>STOMP SEND 帧必须携带 {@code ws-action: <action>} 自定义头，否则消息被忽略
 *   <li>方法的唯一参数类型即消息体反序列化目标类型；由框架调用 {@link
 *       com.njydsz.common.socket.serialize.MessageSerializer MessageSerializer} 反序列化
 *       payload 为参数类型的实例后传入
 * </ul>
 *
 * <p>示例：
 *
 * <pre>{@code
 * @Service
 * public class ChatMessageHandler {
 *     @WebSocketHandler(action = "chat.message")
 *     public void onChatMessage(ChatMessageRequest request, WebSocketSessionAttributes attrs) {
 *         String userId = attrs.getUserId();
 *         // 处理聊天消息...
 *     }
 * }
 * }</pre>
 *
 * <p>带 {@link WebSocketSessionAttributes} 作为第二个参数时，框架自动注入当前 Session 属性
 *（userId、tenantId 等），方便处理器获取上下文信息。
 *
 * @author ydsz-team
 * @since 26.09.20
 * @see WebSocketMessageDispatcher
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WebSocketHandler {

  /**
   * 消息动作标识（用于路由匹配）。
   *
   * <p>客户端在 STOMP SEND 帧中通过自定义请求头 {@code ws-action} 传递此值；框架在路由分发
   * 时严格匹配。同一 action 全局唯一，重复注册时启动失败（fail-fast）。
   *
   * @return 动作名（不可为空或空白）
   */
  String action();

  /**
   * 动作描述（仅供文档和运维诊断使用）。
   *
   * @return 动作描述，默认为 ""
   */
  String description() default "";
}
