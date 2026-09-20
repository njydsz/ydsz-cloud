package com.njydsz.common.docs.exception;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 文档处理模块异常码枚举
 *
 * <p>采用两段式错误码结构（G 段位 + 五位数字），便于按域分类与日志检索：
 *
 * <ul>
 *   <li>G01*** - 解析错误（不支持格式 / 解析失败 / 超时 / 加密等）
 *   <li>G03*** - 安全扫描错误（已知风险 / OLE / 宏 / PDF JS 检测）
 *   <li>G04*** - PII 检测错误
 *   <li>G07*** - 转换错误（格式转换失败）
 *   <li>G99*** - 未知错误（兜底）
 * </ul>
 *
 * <p>每个枚举显式声明 {@link #getHttpStatus()} / {@link #getLevel()} / {@link #getCategory()}， 严格对齐 ydsz-common-exception 规范的三要素协议；安全类异常码（G03 段）映射 SECURITY 分类
 * 与人读友好的 HTTP 状态（{@code 403 Forbidden} / {@code 451 Unavailable For Legal Reasons}）。 级别映射遵循规范：
 *
 * <ul>
 *   <li>文档格式/参数问题（G01001/G01005）→ {@code WARN} 由前端拦截处理</li>
 *   <li>解析/转换失败（G01002/G07001）→ {@code ERROR} 弹窗告警</li>
 *   <li>安全/加密风险（G03 段）→ {@code ERROR} + SECURITY 分类供监控大屏汇总</li>
 *   <li>超时（G01004）→ {@code ERROR} 且 {@link #retryable()} 为 {@code true}</li>
 * </ul>
 *
 * <p><b>稳定性：</b>错误码是业务契约，修改/废弃必须保留向前兼容的 alias， 避免错误码硬编码在客户端代码中后无法平滑升级。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@YdszExceptionCode(module = "docs", description = "文档管理")
public enum DocumentExceptionCode implements ExceptionCode {

  // ── 解析错误 G01 ──────────────────────────────────────────────────────────────

  /** 不支持的文档格式（HTTP 422 Unprocessable Entity：请求格式正确但语义有误） */
  UNSUPPORTED_FORMAT("G01001", "docs.format.unsupported", 422,
      ExceptionLevel.WARN, ExceptionCategory.BUSINESS),
  /** 文档解析失败（HTTP 500 Internal Server Error） */
  PARSE_FAILED("G01002", "docs.parse.failed",
      500,
      ExceptionLevel.ERROR, ExceptionCategory.SYSTEM),
  /** 文档解析超时（HTTP 408 Request Timeout + retryable） */
  PARSE_TIMEOUT("G01004", "docs.parse.timeout",
      408,
      ExceptionLevel.ERROR, ExceptionCategory.SYSTEM),
  /** 文档为空或无法读取（HTTP 422） */
  DOCUMENT_EMPTY("G01005", "docs.empty",
      422,
      ExceptionLevel.WARN, ExceptionCategory.BUSINESS),
  /** 文档已加密无法解析（HTTP 451 Unavailable For Legal Reasons） */
  DOCUMENT_ENCRYPTED("G01006", "docs.encrypted",
      451,
      ExceptionLevel.ERROR, ExceptionCategory.BUSINESS),

  // ── 安全扫描错误 G03 ─────────────────────────────────────────────────────────

  /** 安全扫描过程异常（HTTP 500） */
  SECURITY_SCAN_FAILED("G03001", "docs.security.scan.failed",
      500,
      ExceptionLevel.ERROR, ExceptionCategory.SYSTEM),
  /** 检测到高危安全风险（HTTP 403 Forbidden + SECURITY 分类） */
  SECURITY_RISK_DETECTED("G03002", "docs.security.risk.detected",
      403,
      ExceptionLevel.ERROR, ExceptionCategory.SECURITY),

  // ── PII 检测错误 G04 ─────────────────────────────────────────────────────────

  /** PII 检测过程异常（HTTP 500） */
  PII_DETECTION_FAILED("G04002", "docs.pii.detection.failed",
      500,
      ExceptionLevel.ERROR, ExceptionCategory.SYSTEM),

  // ── 转换错误 G07 ─────────────────────────────────────────────────────────────

  /** 格式转换失败（HTTP 500） */
  CONVERT_FAILED("G07001", "docs.convert.failed",
      500,
      ExceptionLevel.ERROR, ExceptionCategory.SYSTEM),

  // ── 兜底 G99 ─────────────────────────────────────────────────────────────────

  /** 未知错误（HTTP 500 + FATAL 级别） */
  UNKNOWN("G99999", "unknown.error",
      500,
      ExceptionLevel.FATAL, ExceptionCategory.SYSTEM);

  private final String code;
  private final String key;
  private final int httpStatus;
  private final ExceptionLevel level;
  private final ExceptionCategory category;

  DocumentExceptionCode(
      String code, String key, int httpStatus, ExceptionLevel level, ExceptionCategory category) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
    this.level = level;
    this.category = category;
  }

  /**
   * 获取对外暴露的业务错误码。
   *
   * <p>该码会随统一响应体返回给前端并写入日志，是跨系统排障的检索键， 一经发布不可修改，废弃时只能新增码值而非复用旧码。 段位含义见类级注释中的 G01~G99 分区说明。
   *
   * @return 形如 {@code G01001} 的错误码；恒不为 {@code null}
   */
  @Override
  public String getCode() {
    return code;
  }

  /**
   * 获取国际化资源文件中的消息键。
   *
   * <p>由全局异常处理器拿该键去 {@code MessageSource} 查找对应语言的提示文案， 从而实现错误提示与错误码解耦。若资源文件中缺失该键，
   * 框架通常降级为直接展示键名本身，因此新增枚举项时必须同步补齐 i18n 配置。
   *
   * @return 形如 {@code docs.parse.failed} 的点分消息键；恒不为 {@code null}
   */
  @Override
  public String getKey() {
    return key;
  }

  /**
   * HTTP 状态码语义覆盖 —— 返回当前异常码对应的 HTTP 响应状态。
   *
   * <p>由全局异常处理器利用该值填充 HTTP Response Status，使异常与 HTTP 层协议对齐。
   *
   * @return HTTP 状态码（如 422 / 403 / 408 / 451 / 500）
   */
  @Override
  public int getHttpStatus() {
    return httpStatus;
  }

  /**
   * 异常级别 —— 驱动前端展示方式与监控告警等级。
   *
   * <p>约定：{@code WARN}（前端拦截提示）、{@code ERROR}（需用户确认或系统告警）、 {@code FATAL}（阻断式弹窗）。详见类级注释中的级别映射策略。
   *
   * @return 异常级别；恒不为 {@code null}
   */
  @Override
  public ExceptionLevel getLevel() {
    return level;
  }

  /**
   * 异常分类 —— 供监控系统按维度聚合。
   *
   * <p>安全类异常（{@code SECURITY_RISK_DETECTED}）映射 {@link ExceptionCategory#SECURITY}， 便于安全事件大盘统一呈现。
   *
   * @return 异常分类；恒不为 {@code null}
   */
  @Override
  public ExceptionCategory getCategory() {
    return category;
  }

  /**
   * 标记该异常是否可恢复（客户端是否应重试）。
   *
   * <p>仅 {@link #PARSE_TIMEOUT} 为可恢复（建议客户端指数退避重试）； 其他解析 / 安全类异常不可重试。
   *
   * @return 仅超时时返回 {@code true}；其它返回 {@code false}
   */
  @Override
  public boolean retryable() {
    return this == PARSE_TIMEOUT;
  }
}
