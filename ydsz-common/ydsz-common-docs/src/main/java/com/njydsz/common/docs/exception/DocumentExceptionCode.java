package com.njydsz.common.docs.exception;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 文档处理模块异常码枚举
 *
 * <p>采用两段式错误码结构（G 段位 + 五位数字），便于按域分类与日志检索：
 *
 * <ul>
 *   <li>G01*** - 解析错误（不支持格式/解析失败/超时等）
 *   <li>G03*** - 安全扫描错误（宏检测/PDF JS 检测失败）
 *   <li>G04*** - PII 检测错误（检测异常）
 *   <li>G07*** - 转换错误（格式转换失败）
 *   <li>G99*** - 未知错误（兜底）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@YdszExceptionCode(module = "docs", description = "文档管理")
public enum DocumentExceptionCode implements ExceptionCode {

  /** 不支持的文档格式 */
  UNSUPPORTED_FORMAT("G01001", "docs.format.unsupported"),
  /** 文档解析失败 */
  PARSE_FAILED("G01002", "docs.parse.failed"),
  /** 文档解析超时 */
  PARSE_TIMEOUT("G01004", "docs.parse.timeout"),
  /** 文档为空或无法读取 */
  DOCUMENT_EMPTY("G01005", "docs.empty"),
  /** 文档已加密，无法解析 */
  DOCUMENT_ENCRYPTED("G01006", "docs.encrypted"),
  /** 安全扫描失败 */
  SECURITY_SCAN_FAILED("G03001", "docs.security.scan.failed"),
  /** 检测到高危安全风险 */
  SECURITY_RISK_DETECTED("G03002", "docs.security.risk.detected"),
  /** PII 检测异常 */
  PII_DETECTION_FAILED("G04002", "docs.pii.detection.failed"),
  /** 格式转换失败 */
  CONVERT_FAILED("G07001", "docs.convert.failed"),
  /** 未知错误（兜底） */
  UNKNOWN("G99999", "unknown.error");

  private final String code;
  private final String key;

  DocumentExceptionCode(String code, String key) {
    this.code = code;
    this.key = key;
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
}
