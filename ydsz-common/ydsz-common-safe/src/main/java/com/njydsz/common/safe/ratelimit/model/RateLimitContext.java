new HashMap<>(16)6)package com.njydsz.common.safe.ratelimit.model;

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
  @Builder.Default private Map<String, Object> attributes = new HashMap<>();

  /** 热点参数索引（按位置 0,1,2,3...） */
  @Builder.Default private Map<Integer, Object> hotParams = new HashMap<>();

  /** 方法入参（按位置） */
  @Builder.Default private Object[] args = new Object[] {};

  /** 方法签名（用于 AOP） */
  private String methodSignature;

  /**
   * 添加上下文属性。
   *
   * @param key 属性键
   * @param value 属性值
   * @return 当前上下文（链式调用）
   */
  public RateLimitContext put(String key, Object value) {
    if (this.attributes == null) {
      this.attributes = new HashMap<>();
    }
    this.attributes.put(key, value);
    return this;
  }

  /**
   * 添加热点参数。
   *
   * @param index 参数索引
   * @param value 参数值
   * @return 当前上下文（链式调用）
   */
  public RateLimitContext putHotParam(int index, Object value) {
    if (this.hotParams == null) {
      this.hotParams = new HashMap<>(16);
    }
    this.hotParams.put(index, value);
    return this;
  }

  /**
   * 获取属性。
   *
   * @param key 属性键
   * @return 属性值（不存在时为 {@code null}）
   */
  public Object get(String key) {
    return attributes == null ? null : attributes.get(key);
  }

  /**
   * 获取属性（带默认值）。
   *
   * @param key 属性键
   * @param defaultValue 默认值
   * @return 属性值（不存在时返回默认值）
   */
  public Object getOrDefault(String key, Object defaultValue) {
    return attributes == null ? defaultValue : attributes.getOrDefault(key, defaultValue);
  }
}
