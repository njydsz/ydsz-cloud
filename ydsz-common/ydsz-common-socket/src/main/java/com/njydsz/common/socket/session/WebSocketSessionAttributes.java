package com.njydsz.common.socket.session;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import com.njydsz.common.socket.constant.WebSocketConstants;

/**
 * WebSocket Session 属性视图（FEAT-002 配套类）。
 *
 * <p>提供类型安全的 Session 属性访问接口，封装 {@code Map<String, Object>} 避免裸 Map 传递。
 *
 * <p>实例由框架在 C→S 消息分发时构造，注入到 {@link
 * com.njydsz.common.socket.handler.WebSocketHandler @WebSocketHandler} 方法的第二个参数位置。
 *
 * <p>支持的属性键由 {@link com.njydsz.common.socket.constant.WebSocketConstants} 定义，常用键包括：
 *
 * <ul>
 *   <li>{@code WS_ATTR_USER_ID} — 用户 ID
 *   <li>{@code WS_ATTR_TENANT_ID} — 租户 ID（ARCH-005 租户隔离）
 *   <li>{@code WS_ATTR_USERNAME} — 用户名
 * </ul>
 *
 * <p>本类为不可变值对象，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public final class WebSocketSessionAttributes {

  /** 空实例（不可变空映射） */
  // YDIZ-WARN-001 允许保留：WebSocket 会话属性键名复用字符串，避免重复字面量
  @SuppressWarnings("java:S1192")
  public static final WebSocketSessionAttributes EMPTY =
      new WebSocketSessionAttributes(Collections.emptyMap());

  private final Map<String, Object> attributes;

  private WebSocketSessionAttributes(Map<String, Object> attributes) {
    this.attributes = attributes;
  }

  /**
   * 从 Session 属性 Map 构造视图。
   *
   * @param attributes STOMP Session 属性，为 null 时降级为空视图
   * @return 属性视图实例（永远不为 null）
   */
  public static WebSocketSessionAttributes of(Map<String, Object> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      return EMPTY;
    }
    return new WebSocketSessionAttributes(Map.copyOf(attributes));
  }

  /**
   * 获取用户 ID。
   *
   * @return 用户 ID，不存在时返回 null
   */
  public String getUserId() {
    return getString(WebSocketConstants.WS_ATTR_USER_ID);
  }

  /**
   * 获取租户 ID（ARCH-005 租户隔离）。
   *
   * @return 租户 ID，不存在时返回 null
   */
  public String getTenantId() {
    return getString(WebSocketConstants.WS_ATTR_TENANT_ID);
  }

  /**
   * 获取用户名。
   *
   * @return 用户名，不存在时返回 null
   */
  public String getUsername() {
    return getString(WebSocketConstants.WS_ATTR_USERNAME);
  }

  /**
   * 按 key 获取原始属性值（不推荐业务方直接使用，应使用类型安全的访问方法）。
   *
   * @param key 属性键
   * @return 值对象，不存在时返回 null
   */
  public Object getRaw(String key) {
    return attributes.get(key);
  }

  /**
   * 按 key 获取 String 类型属性值。
   *
   * @param key 属性键
   * @return String 值，不存在或类型不匹配时返回 null
   */
  public String getString(String key) {
    Object value = attributes.get(key);
    return value instanceof String s ? s : null;
  }

  /**
   * 是否包含指定属性键。
   *
   * @param key 属性键
   * @return true 表示存在该键
   */
  public boolean contains(String key) {
    return attributes.containsKey(key);
  }

  /**
   * 属性数量。
   *
   * @return 属性条数
   */
  public int size() {
    return attributes.size();
  }

  /**
   * 是否为空（无任何属性）。
   *
   * @return true 表示空视图
   */
  public boolean isEmpty() {
    return attributes.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WebSocketSessionAttributes that)) {
      return false;
    }
    return Objects.equals(attributes, that.attributes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attributes);
  }

  @Override
  public String toString() {
    return "WebSocketSessionAttributes{size=" + attributes.size() + "}";
  }
}
