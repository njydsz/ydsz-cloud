package com.njydsz.common.socket.acl;

import java.util.Map;

/**
 * 订阅主题 ACL 策略接口（FEAT-003）。
 *
 * <p>在客户端 SUBSCRIBE 帧到达时决定该用户是否有权订阅指定 Destination。实现方可基于
 * 用户 ID、租户 ID、Destination 路径模式匹配等维度进行细粒度鉴权。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>返回 false 时拦截（订阅被允许）
 *   <li>返回 true 时放行（订阅被允许）
 *   <li>接口实现应实现为轻量、无阻塞（避免在 message broker 热点路径上引入 IO 延迟）
 * </ul>
 *
 * <p>业务方可通过注册自定义实现覆盖默认策略（{@code DefaultTopicAclPolicy}）；不注册时
 * 使用默认开放策略（仅拦截 /admin/** 等受保护前缀）。
 *
 * <p>典型用法：在实现中利用 userId/tenantId 校验用户是否有权访问其订阅的
 * /topic/user/{userId}/... 路径，防止水平越权（如用户 A 订阅 /topic/user/B/...）。
 *
 * <hr>
 *
 * <p>设计原则：Spring Security STOMP 支持已在 Spring Security 中完整实现，本接口提供
 * 一个更轻量的"最小集"——仅做 destination 模式匹配 + 用户维度鉴权，不引入
 * 完整的 Spring Security 依赖。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public interface TopicAclPolicy {

  /**
   * 检查指定用户是否允许订阅目标路径。
   *
   * <p>实现类在抛出异常时应谨慎——框架将异常视为"不通过"（保守策略），可能误伤。
   * 建议在内部 catch 所有异常并显式记录日志、返回保守结果。
   *
   * @param userId 订阅用户 ID（STOMP Session 属性 ws-user-id，不可为 null）
   * @param destination 目标订阅路径（STOMP header: destination，如 "/topic/user/123/notifications"）
   * @param sessionAttributes 完整的 Session 属性映射（含 userId/tenantId，供高级策略使用）
   * @return true 表示允许订阅，false 表示拒绝（框架向客户端发送 ERROR 帧并关闭订阅）
   */
  boolean allowSubscription(
      String userId,
      String destination,
      Map<String, Object> sessionAttributes);
}
