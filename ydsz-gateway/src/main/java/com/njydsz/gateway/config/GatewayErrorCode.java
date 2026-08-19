package com.njydsz.gateway.config;

import lombok.Getter;

/**
 * P0-3: 网关层统一错误码规范。
 *
 * <p>对标互联网大厂网关错误码体系（阿里 ASEN / 美团 Shepherd / Kong / APISIX），
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
 * @since 1.0.0
 * @author ydsz-team
 * @see <a href="https://docs.njydsz.com/errors">错误码文档</a>
 */
@Getter
public enum GatewayErrorCode {

  // ===== 2xx 成功（占位，网关层无需单独定义） =====
  SUCCESS(20000, "error.SUCCESS"),

  // ===== 400xx 请求参数错误 =====
  BAD_REQUEST(40000, "error.BAD_REQUEST"),
  PATH_TRAVERSAL(40001, "error.PATH_TRAVERSAL"),
  PAYLOAD_TOO_LARGE(40002, "error.PAYLOAD_TOO_LARGE"),
  CONTENT_TYPE_MISSING(40003, "error.CONTENT_TYPE_MISSING"),
  INVALID_PARAMETER(40004, "error.INVALID_PARAMETER"),

  // ===== 401xx 认证失败 =====
  UNAUTHORIZED(40100, "error.UNAUTHORIZED"),
  TOKEN_INVALID(40101, "error.TOKEN_INVALID"),
  TOKEN_EXPIRED(40102, "error.TOKEN_EXPIRED"),
  TOKEN_BLACKLISTED(40103, "error.TOKEN_BLACKLISTED"),
  REPLAY_DETECTED(40104, "error.REPLAY_DETECTED"),
  API_KEY_MISSING(40105, "error.API_KEY_MISSING"),
  API_KEY_INVALID(40106, "error.API_KEY_INVALID"),

  // ===== 403xx 权限不足 =====
  FORBIDDEN(40300, "error.FORBIDDEN"),
  IP_FORBIDDEN(40301, "error.IP_FORBIDDEN"),
  IP_BLACKLISTED(40302, "error.IP_BLACKLISTED"),
  ORIGIN_FORBIDDEN(40303, "error.ORIGIN_FORBIDDEN"),

  // ===== 404xx 资源/路由不存在 =====
  ROUTE_NOT_FOUND(40400, "error.ROUTE_NOT_FOUND"),

  // ===== 408xx 请求超时 =====
  REQUEST_TIMEOUT(40800, "error.REQUEST_TIMEOUT"),

  // ===== 429xx 限流 =====
  RATE_LIMITED(42900, "error.RATE_LIMITED"),
  RATE_LIMITED_IP(42901, "error.RATE_LIMITED_IP"),
  RATE_LIMITED_USER(42902, "error.RATE_LIMITED_USER"),

  // ===== 500xx 网关内部错误 =====
  INTERNAL_ERROR(50000, "error.INTERNAL_ERROR"),

  // ===== 502xx 下游服务异常 =====
  BAD_GATEWAY(50200, "error.BAD_GATEWAY"),

  // ===== 503xx 熔断/服务不可用 =====
  SERVICE_UNAVAILABLE(50300, "error.SERVICE_UNAVAILABLE"),
  CIRCUIT_BREAKER_OPEN(50301, "error.CIRCUIT_BREAKER_OPEN"),

  // ===== 504xx 下游响应超时 =====
  GATEWAY_TIMEOUT(50400, "error.GATEWAY_TIMEOUT");

  /** 5 位业务错误码 */
  private final int code;

  /** i18n 消息键（前端根据此键 + 语言环境翻译） */
  private final String messageKey;

  GatewayErrorCode(int code, String messageKey) {
    this.code = code;
    this.messageKey = messageKey;
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
