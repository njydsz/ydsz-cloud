package com.njydsz.common.tenant.feign;

import java.util.Map;

import com.njydsz.common.core.context.TenantContextHolder;

/**
 * 租户上下文传播策略 SPI：支持不同传输协议/框架下的上下文透传扩展。
 *
 * <p>内置实现：
 *
 * <ul>
 *   <li>HTTP/Feign → {@link TenantContextFeignInterceptor}（通过 Header 透传）
 *   <li>Web Filter → {@code TenantContextWebFilter}（通过 HTTP Header / JWT 解析）
 * </ul>
 *
 * <p>扩展实现示例：
 *
 * <pre>{@code
 * // GraphQL（Netflix DGS / GraphQL Java）传播策略
 * &#64;Component
 * &#64;Primary
 * public class GraphQlTenantPropagation implements TenantContextPropagationStrategy {
 *
 *     &#64;Override
 *     public void propagate(Map<String, String> headers) {
 *         // 从 GraphQL DataFetcher 的 ResolutionEnvironment 中提取上下文
 *         TenantContext ctx = TenantContextHolder.get();
 *         if (ctx != null) {
 *             headers.put("x-tenant-id", ctx.getTenantId());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>本 SPI 为预留接口，供 GraphQL、gRPC、消息队列等非 HTTP 框架实现自定义租户传播逻辑。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see TenantContextHolder
 * @see TenantContextFeignInterceptor
 */
public interface TenantContextPropagationStrategy {

  /**
   * 将当前租户上下文注入到传输载体（Header / Metadata / Exchange Attributes）。
   *
   * <p>实现类应从 {@link TenantContextHolder} 获取当前上下文，将相关字段写入目标载体。
   * 若无上下文则不写入（由目标框架自身的上下文恢复机制处理）。
   *
   * @param transportCarrier 传输载体的 key-value 容器（如 HttpHeaders / Metadata Map / GraphQL Context Map）
   */
  void propagate(Map<String, String> transportCarrier);

  /**
   * 从传输载体恢复当前租户上下文到 ThreadLocal。
   *
   * <p>在消费端（RPC 服务端 / MQ Consumer）调用，用于从请求元数据中提取租户信息并设置到 ThreadLocal。
   *
   * @param transportCarrier 传输载体的 key-value 容器
   */
  default void restore(Map<String, String> transportCarrier) {
    // 默认空实现；各框架根据自身协议实现
  }

  /**
   * 策略优先级（值越小优先级越高）。
   *
   * <p>当注册多个策略时，按优先级降序遍历尝试传播/恢复。
   *
   * @return 优先级（默认 {@link Integer#MAX_VALUE}，即最低优先级）
   */
  default int order() {
    return Integer.MAX_VALUE;
  }

  /**
   * 当前策略是否支持指定类型的传输载体。
   *
   * @param transportType 传输载体类型标识（如 {@code "graphql"} / {@code "grpc"} / {@code "kafka"}）
   * @return true=支持
   */
  default boolean supports(String transportType) {
    return false;
  }
}
