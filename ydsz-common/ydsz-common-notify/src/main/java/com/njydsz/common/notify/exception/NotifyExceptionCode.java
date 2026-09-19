package com.njydsz.common.notify.exception;

/**
 * 通知模块错误码枚举（P0-2 错误码体系建设）。
 *
 * <p>定义通知发送全链路中各类失败场景的细粒度错误码，使调用方可编程区分失败原因， 据此决定重试、降级、告警或忽略策略。
 *
 * <p><b>错误码命名规则</b>：
 *
 * <ul>
 *   <li>{@code CFG_} 前缀：配置错误（CONFIG_ERROR）
 *   <li>{@code CHN_} 前缀：渠道不可用（CHANNEL_UNAVAILABLE）
 *   <li>{@code RATE_} 前缀：限流触发（RATE_LIMITED）
 *   <li>{@code CB_} 前缀：熔断触发（CIRCUIT_BREAKER_OPEN）
 *   <li>{@code CTX_} 前缀：内容非法（CONTENT_REJECTED）
 *   <li>{@code RCV_} 前缀：接收方非法（RECEIVER_INVALID）
 *   <li>{@code TPL_} 前缀：模板问题（TEMPLATE_MISSING）
 *   <li>{@code NET_} 前缀：网络异常（NETWORK_ERROR）
 *   <li>{@code INT_} 前缀：内部错误（INTERNAL_ERROR）
 * </ul>
 *
 * <p><b>使用方式：</b>
 *
 * <pre>{@code
 * throw new NotifyException(NotifyExceptionCode.CHANNEL_UNAVAILABLE, "SMTP 服务不健康");
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public enum NotifyExceptionCode {

  // ==================== 配置错误 ====================

  /** 通知渠道配置缺失或无效 */
  CFG_INVALID("NTF_CFG_001", "通知渠道配置缺失或无效"),

  /** SMTP 连接参数未配置 */
  CFG_SMTP_NOT_CONFIGURED("NTF_CFG_002", "SMTP 连接参数未配置"),

  /** 短信 API endpoint 未配置 */
  CFG_SMS_ENDPOINT_MISSING("NTF_CFG_003", "短信 API endpoint 未配置"),

  // ==================== 渠道不可用 ====================

  /** 通知渠道未启用 */
  CHN_NOT_ENABLED("NTF_CHN_001", "通知渠道未启用"),

  /** 通知渠道未配置 */
  CHN_NOT_CONFIGURED("NTF_CHN_002", "通知渠道未配置"),

  /** SMTP 服务不健康 */
  CHN_SMTP_UNHEALTHY("NTF_CHN_003", "SMTP 服务不健康"),

  /** Redis 不可用，无法发送站内信 */
  CHN_REDIS_UNAVAILABLE("NTF_CHN_004", "Redis 不可用，无法发送站内信"),

  // ==================== 限流 ====================

  /** 通知渠道限流触发 */
  RATE_LIMITED("NTF_RATE_001", "通知渠道限流触发，请稍后重试"),

  // ==================== 熔断 ====================

  /** 通知渠道已熔断 */
  CB_OPEN("NTF_CB_001", "通知渠道已熔断，请稍后重试"),

  // ==================== 内容非法 ====================

  /** 邮件内容被 XSS 清洗后为空 */
  CTX_CONTENT_EMPTY("NTF_CTX_001", "邮件内容被 XSS 清洗后为空"),

  // ==================== 接收方非法 ====================

  /** 收件人邮箱地址无效 */
  RCV_EMAIL_INVALID("NTF_RCV_001", "收件人邮箱地址无效"),

  /** 手机号为空 */
  RCV_PHONE_EMPTY("NTF_RCV_002", "手机号为空"),

  /** 接收者 ID 为空 */
  RCV_USER_ID_EMPTY("NTF_RCV_003", "接收者 ID 为空"),

  /** 收件人列表为空 */
  RCV_LIST_EMPTY("NTF_RCV_004", "收件人列表为空"),

  // ==================== 模板问题 ====================

  /** 模板不存在 */
  TPL_NOT_FOUND("NTF_TPL_001", "模板不存在"),

  /** 模板引擎不可用 */
  TPL_ENGINE_MISSING("NTF_TPL_002", "模板引擎不可用"),

  // ==================== 网络异常 ====================

  /** 网络超时 */
  NET_TIMEOUT("NTF_NET_001", "网络超时"),

  /** 网络连接失败 */
  NET_CONNECT_FAILED("NTF_NET_002", "网络连接失败"),

  /** 外部服务商返回错误 */
  NET_EXTERNAL_ERROR("NTF_NET_003", "外部服务商返回错误"),

  // ==================== 内部错误 ====================

  /** 内部发送异常 */
  INT_SEND_ERROR("NTF_INT_001", "内部发送异常"),

  /** 短信签名计算失败 */
  INT_SMS_SIGN_ERROR("NTF_INT_002", "短信签名计算失败");

  /** 错误码 */
  private final String code;

  /** 错误描述 */
  private final String description;

  NotifyExceptionCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
