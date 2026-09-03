package com.njydsz.common.safe.ratelimit.model;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流上下文
 *
 * <p>限流决策时使用的上下文信息，包括资源名、维度信息、热点参数、用户/IP 等。 由 AOP 切面在调用前自动构建。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * RateLimitContext ctx = RateLimitContext.builder()
 *     .resource("order.create")
 *     .put("userId", "12345")
 *     .put("skuId", "67890")
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitContext implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 资源名（接口名/方法名） */
  private String resource;

  /** 限流 key 上下文（用户 ID、IP、租户 ID 等） */
  @Builder.Default private Map<String, Object> attributes = new HashMap<>(16);

  /** 请求时间戳（毫秒） */
  private long timestamp;

  /** 方法参数（用于 keyParam 提取和审计） */
  private Object[] args;

  /** 方法签名（用于审计和方法级识别） */
  private String methodSignature;

  /**
   * Builder 辅助方法：创建一个带有单个键值对的 {@link RateLimitContext} 构建器。
   *
   * @param key 键
   * @param value 值
   * @return 新的 RateLimitContext 实例（仅包含该属性）
   */
  public static RateLimitContext put(String key, Object value) {
    Map<String, Object> attrs = new HashMap<>(16);
    attrs.put(key, value);
    return RateLimitContext.builder().attributes(attrs).build();
  }
}
