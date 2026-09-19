package com.njydsz.common.auth.problem;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * RFC 7807 Problem Detail 响应体。
 *
 * <p>遵循 <a href="https://tools.ietf.org/html/rfc7807">RFC 7807</a> 标准，为认证域错误提供统一、机器可读的错误描述。
 *
 * <p>JSON 示例：
 *
 * <pre>{@code
 * {
 *   "type": "https://ydsz.com/probs/permission-denied",
 *   "title": "接口权限不足",
 *   "status": 403,
 *   "detail": "缺少接口权限 sys:user:delete",
 *   "instance": "/api/v1/user",
 *   "errorCode": "C01063",
 *   "context": {
 *     "userId": "user-001",
 *     "requiredPermissions": ["sys:user:delete"],
 *     "grantedPermissions": ["sys:user:view"]
 *   }
 * }
 * }</pre>
 *
 * <p>必填字段：type、title、status；选填字段：detail、instance；扩展字段：errorCode、context。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class ApiProblemDetail implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 标识错误类型的 URI（默认 "about:blank"，建议指向内部错误文档）。 */
  private final String type;

  /** 面向人类可读的简短错误标题（英文/中文唯一描述，不随调用变化）。 */
  private final String title;

  /** HTTP 状态码。 */
  private final int status;

  /** 面向开发者的详细错误描述（可随调用变化）。 */
  private final String detail;

  /** 发生错误的请求 URI。 */
  private final String instance;

  /** YDSZ 内部错误码（如 C01063）。 */
  private final String errorCode;

  /** 结构化上下文信息（扩展字段）。 */
  private final Map<String, Object> context;

  private ApiProblemDetail(Builder builder) {
    this.type = builder.type != null ? builder.type : "about:blank";
    this.title = builder.title;
    this.status = builder.status;
    this.detail = builder.detail;
    this.instance = builder.instance;
    this.errorCode = builder.errorCode;
    this.context = builder.context != null ? Map.copyOf(builder.context) : Map.of();
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public int getStatus() {
    return status;
  }

  public String getDetail() {
    return detail;
  }

  public String getInstance() {
    return instance;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public Map<String, Object> getContext() {
    return context;
  }

  /**
   * 创建 Builder 实例。
   *
   * @return Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 预构建的"403 权限不足"问题类型。
   *
   * @param detail 具体错误描述
   * @param instance 请求 URI
   * @return Builder (已设置 type/title/status)，可链式添加上下文
   */
  public static Builder permissionDenied(String detail, String instance) {
    return builder()
        .type("https://ydsz.com/probs/permission-denied")
        .title("权限不足")
        .status(403)
        .detail(detail)
        .instance(instance);
  }

  /**
   * 预构建的"401 身份认证失败"问题类型。
   *
   * @param detail 具体错误描述
   * @return Builder (已设置 type/title/status)，可链式添加上下文
   */
  public static Builder unauthorized(String detail) {
    return builder()
        .type("https://ydsz.com/probs/unauthorized")
        .title("身份认证失败")
        .status(401)
        .detail(detail);
  }

  /**
   * 预构建的"423 账号已锁定"问题类型。
   *
   * @param detail 具体错误描述（建议包含锁定倒计时）
   * @return Builder (已设置 type/title/status)，可链式添加上下文
   */
  public static Builder accountLocked(String detail) {
    return builder()
        .type("https://ydsz.com/probs/account-locked")
        .title("账号已锁定")
        .status(423)
        .detail(detail);
  }

  /**
   * 预构建的"429 请求过于频繁"问题类型。
   *
   * @param detail 具体错误描述
   * @return Builder (已设置 type/title/status)，可链式添加上下文
   */
  public static Builder tooManyRequests(String detail) {
    return builder()
        .type("https://ydsz.com/probs/too-many-requests")
        .title("请求过于频繁")
        .status(429)
        .detail(detail);
  }

  /** ApiProblemDetail 构建器。 */
  public static class Builder {
    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private String errorCode;
    private Map<String, Object> context;

    /**
     * 设置错误类型 URI。
     *
     * @param type URI 字符串
     * @return 当前构建器
     */
    public Builder type(String type) {
      this.type = type;
      return this;
    }

    /**
     * 设置错误标题。
     *
     * @param title 简短标题
     * @return 当前构建器
     */
    public Builder title(String title) {
      this.title = title;
      return this;
    }

    /**
     * 设置 HTTP 状态码。
     *
     * @param status HTTP 状态码（4xx/5xx）
     * @return 当前构建器
     */
    public Builder status(int status) {
      this.status = status;
      return this;
    }

    /**
     * 设置详细错误描述。
     *
     * @param detail 面向开发者的详细描述
     * @return 当前构建器
     */
    public Builder detail(String detail) {
      this.detail = detail;
      return this;
    }

    /**
     * 设置请求 URI。
     *
     * @param instance 发生错误的资源路径
     * @return 当前构建器
     */
    public Builder instance(String instance) {
      this.instance = instance;
      return this;
    }

    /**
     * 设置 YDSZ 内部错误码。
     *
     * @param errorCode 错误码（如 "C01063"）
     * @return 当前构建器
     */
    public Builder errorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
    }

    /**
     * 添加上下文信息（键值对）。
     *
     * @param key 上下文键
     * @param value 上下文值
     * @return 当前构建器
     */
    public Builder context(String key, Object value) {
      if (this.context == null) {
        this.context = new HashMap<>(8);
      }
      this.context.put(key, value);
      return this;
    }

    /**
     * 批量设置上下文信息。
     *
     * @param contextMap 上下文 Map（非 null）
     * @return 当前构建器
     */
    public Builder context(Map<String, Object> contextMap) {
      if (contextMap != null) {
        if (this.context == null) {
          this.context = new HashMap<>(contextMap.size());
        }
        this.context.putAll(contextMap);
      }
      return this;
    }

    /**
     * 构建 {@link ApiProblemDetail} 实例。
     *
     * @return 构建完成的实例
     */
    public ApiProblemDetail build() {
      return new ApiProblemDetail(this);
    }
  }
}
