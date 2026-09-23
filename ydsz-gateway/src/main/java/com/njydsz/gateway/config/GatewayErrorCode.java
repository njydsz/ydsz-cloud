package com.njydsz.gateway.config;

import java.util.List;

import lombok.Getter;

/**
 * P0-3: 网关层统一错误码规范。
 *
 * <p>网关错误码体系，
 * 为网关层所有错误响应分配标准化业务码（bizCode），前端和下游系统可据此做精准错误处理。
 *
 * <h3>错误码结构</h3>
 *
 * <pre>
 * HTTP 状态码 (3 位) + 网关错误分类 (2 位) = 业务码 (5 位)
 *
 * 分类规则：
 *   400xx — 请求参数/格式错误（客户端责任）
 *   401xx — 认证失败（身份不可识别）
 *   403xx — 权限不足（身份已识别但无权访问）
 *   404xx — 路由不存在
 *   408xx — 请求超时
 *   429xx — 限流触发
 *   500xx — 网关内部错误
 *   502xx — 下游服务异常
 *   503xx — 熔断/服务不可用
 *   504xx — 下游响应超时
 * </pre>
 *
 * <h3>前端处理建议</h3>
 *
 * <ul>
 *   <li>401xx → 跳转登录页
 *   <li>403xx → 提示无权限 + 联系管理员
 *   <li>429xx → 展示倒计时 + 禁用提交按钮
 *   <li>502xx / 503xx / 504xx → 提示"服务繁忙，请稍后"
 *   <li>500xx → 提示"系统异常" + 自动上报 Sentry
 * </ul>
 *
 * <h3>i18n 消息键</h3>
 *
 * <p>所有错误响应的 {@code message} 字段使用 i18n 键（如 {@code error.TOKEN_INVALID}）， 前端根据 {@code
 * Accept-Language} 头或用户设置的语言环境翻译。 后端不负责翻译，仅提供统一键名。
 *
 * @since 26.09.01
 * @author ydsz-team
 * @see <a href="https://docs.njydsz.com/errors">错误码文档</a>
 */
@Getter
public enum GatewayErrorCode {

  // ===== 2xx 成功（占位，网关层无需单独定义） =====
  /** SUCCESS */
  SUCCESS(20000, "error.SUCCESS"),

  // ===== 400xx 请求参数错误 =====
  /** BAD_REQUEST */
  BAD_REQUEST(40000, "error.BAD_REQUEST"),
  /** PATH_TRAVERSAL */
  PATH_TRAVERSAL(40001, "error.PATH_TRAVERSAL",
      List.of("请求路径包含非法字符序列，请检查 URL 构造是否正确",
          "合法路径不应包含 .. 或 %2e%2e 等目录跳跃序列")),
  /** PAYLOAD_TOO_LARGE */
  PAYLOAD_TOO_LARGE(40002, "error.PAYLOAD_TOO_LARGE"),
  /** CONTENT_TYPE_MISSING */
  CONTENT_TYPE_MISSING(40003, "error.CONTENT_TYPE_MISSING"),
  /** INVALID_PARAMETER */
  INVALID_PARAMETER(40004, "error.INVALID_PARAMETER"),
  /** SQL_INJECTION_DETECTED */
  SQL_INJECTION_DETECTED(40010, "error.SQL_INJECTION_DETECTED",
      List.of("请求参数包含 SQL 特殊字符，请检查输入")),
  /** XSS_DETECTED */
  XSS_DETECTED(40011, "error.XSS_DETECTED",
      List.of("请求包含跨站脚本攻击特征，已被拦截")),
  /** IDEMPOTENT_DUPLICATE — 幂等 Key 重复请求被拦截（客户端可安全重试 PUT/POST）。 */
  IDEMPOTENT_DUPLICATE(40012, "error.IDEMPOTENT_DUPLICATE",
      List.of("相同 X-Idempotency-Key 的请求已在处理中，请勿重复提交",
          "如确需重试，请使用新的 X-Idempotency-Key 值")),

  // ===== 401xx 认证失败 =====
  /** UNAUTHORIZED */
  UNAUTHORIZED(40100, "error.UNAUTHORIZED",
      List.of("请检查 Authorization 请求头格式是否为 Bearer <token>",
          "Token 可能已过期，请调用 /auth/refresh 刷新")),
  /** TOKEN_INVALID */
  TOKEN_INVALID(40101, "error.TOKEN_INVALID",
      List.of("Token 签名无效，请重新登录获取有效 Token")),
  /** TOKEN_EXPIRED */
  TOKEN_EXPIRED(40102, "error.TOKEN_EXPIRED",
      List.of("Token 已过期，请调用 /auth/refresh 或重新登录")),
  /** TOKEN_BLACKLISTED */
  TOKEN_BLACKLISTED(40103, "error.TOKEN_BLACKLISTED",
      List.of("Token 已被撤销（可能因修改密码或安全策略），请重新登录")),
  /** REPLAY_DETECTED */
  REPLAY_DETECTED(40104, "error.REPLAY_DETECTED",
      List.of("请求存在重放攻击特征，请确保每次请求使用唯一的 X-Request-Id")),
  /** API_KEY_MISSING */
  API_KEY_MISSING(40105, "error.API_KEY_MISSING"),
  /** API_KEY_INVALID */
  API_KEY_INVALID(40106, "error.API_KEY_INVALID"),
  /** DEVICE_SCOPE_MISMATCH — 跨终端 token 使用被拒绝 */
  DEVICE_SCOPE_MISMATCH(40107, "error.DEVICE_SCOPE_MISMATCH"),

  // ===== 403xx 权限不足 =====
  /** FORBIDDEN */
  FORBIDDEN(40300, "error.FORBIDDEN",
      List.of("当前账号无该资源的访问权限，请联系管理员授权")),
  /** IP_FORBIDDEN */
  IP_FORBIDDEN(40301, "error.IP_FORBIDDEN"),
  /** IP_BLACKLISTED */
  IP_BLACKLISTED(40302, "error.IP_BLACKLISTED"),
  /** ORIGIN_FORBIDDEN */
  ORIGIN_FORBIDDEN(40303, "error.ORIGIN_FORBIDDEN"),

  // ===== 404xx 资源/路由不存在 =====
  /** ROUTE_NOT_FOUND */
  ROUTE_NOT_FOUND(40400, "error.ROUTE_NOT_FOUND"),

  // ===== 408xx 请求超时 =====
  /** REQUEST_TIMEOUT */
  REQUEST_TIMEOUT(40800, "error.REQUEST_TIMEOUT"),

  // ===== 429xx 限流 =====
  /** RATE_LIMITED */
  RATE_LIMITED(42900, "error.RATE_LIMITED",
      List.of("请降低请求频率，遵守接口调用间隔限制",
          "如业务确实需要更高配额，请联系管理员申请提额",
          "根据 Retry-After 响应头控制重试节奏")),
  /** RATE_LIMITED_IP */
  RATE_LIMITED_IP(42901, "error.RATE_LIMITED_IP",
      List.of("当前 IP 触发限流，请降低该 IP 的请求频率")),
  /** RATE_LIMITED_USER */
  RATE_LIMITED_USER(42902, "error.RATE_LIMITED_USER",
      List.of("当前用户触发限流，请降低请求频率或申请提额")),

  // ===== 500xx 网关内部错误 =====
  /** INTERNAL_ERROR */
  INTERNAL_ERROR(50000, "error.INTERNAL_ERROR",
      List.of("网关内部错误，请联系运维人员",
          "请保留 X-Trace-Id 响应头以便排查")),

  // ===== 502xx 下游服务异常 =====
  /** BAD_GATEWAY */
  BAD_GATEWAY(50200, "error.BAD_GATEWAY",
      List.of("下游服务返回异常响应，请稍后重试")),

  // ===== 503xx 熔断/服务不可用 =====
  /** SERVICE_UNAVAILABLE */
  SERVICE_UNAVAILABLE(50300, "error.SERVICE_UNAVAILABLE",
      List.of("服务暂时不可用，请稍后重试")),
  /** CIRCUIT_BREAKER_OPEN */
  CIRCUIT_BREAKER_OPEN(50301, "error.CIRCUIT_BREAKER_OPEN",
      List.of("下游服务异常触发熔断，系统正在自动恢复中",
          "熔断恢复时间视下游服务健康状态而定")),

  // ===== 504xx 下游响应超时 =====
  /** GATEWAY_TIMEOUT */
  GATEWAY_TIMEOUT(50400, "error.GATEWAY_TIMEOUT",
      List.of("下游服务响应超时，请稍后重试或检查下游服务状态"));

  /** 5 位业务错误码 */
  private final int code;

  /** i18n 消息键（前端根据此键 + 语言环境翻译） */
  private final String messageKey;

  /**
   * 针对该错误码的建议操作列表。
   *
   * <p>前端 / 调用方可据此展示给用户的可行动作集合。{@link GatewayErrorWriter} 在构建错误响应时将此列表
   * 序列化为 {@code suggestions} 字段，帮助调用方理解错误并采取正确的后续操作。
   */
  private final List<String> suggestions;

  GatewayErrorCode(int code, String messageKey) {
    this(code, messageKey, List.of());
  }

  GatewayErrorCode(int code, String messageKey, List<String> suggestions) {
    this.code = code;
    this.messageKey = messageKey;
    this.suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
  }

  /**
   * 获取格式化后的帮助文档链接。
   *
   * <p>前端可据此渲染"查看详细错误说明"链接，跳转到文档站。
   *
   * @return 帮助文档 URL
   */
  public String getHelpUrl() {
    return "https://docs.njydsz.com/errors/" + code;
  }

  /**
   * 根据业务码数值查找对应枚举。
   *
   * @param code 业务码数值
   * @return 对应枚举，未找到返回 {@link #INTERNAL_ERROR}
   */
  public static GatewayErrorCode fromCode(int code) {
    for (GatewayErrorCode errorCode : values()) {
      if (errorCode.code == code) {
        return errorCode;
      }
    }
    return INTERNAL_ERROR;
  }
}
