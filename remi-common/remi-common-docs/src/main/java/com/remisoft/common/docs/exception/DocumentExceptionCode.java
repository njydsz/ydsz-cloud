package com.remisoft.common.docs.exception;

import com.remisoft.common.exception.enums.ExceptionCode;

/**
 * 文档处理模块异常码枚举
 * <p>
 * 采用两段式错误码结构（G 段位 + 五位数字），便于按域分类与日志检索：
 * <ul>
 *   <li>G01*** - 解析错误（不支持格式/解析失败/内存超限等）</li>
 *   <li>G02*** - 预处理错误（归一化/清洗/分块失败）</li>
 *   <li>G03*** - 安全扫描错误（宏检测/PDF JS 检测失败）</li>
 *   <li>G04*** - PII 检测错误（正则编译失败/检测异常）</li>
 *   <li>G05*** - 脱敏错误（PDF 脱敏/文本脱敏失败）</li>
 *   <li>G06*** - 水印错误（水印添加失败）</li>
 *   <li>G07*** - 转换错误（格式转换失败）</li>
 *   <li>G99*** - 未知错误（兜底）</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum DocumentExceptionCode implements ExceptionCode {

    /** 不支持的文档格式 */
    UNSUPPORTED_FORMAT("G01001", "docs.format.unsupported"),
    /** 文档解析失败 */
    PARSE_FAILED("G01002", "docs.parse.failed"),
    /** 文档解析内存超限 */
    PARSE_MEMORY_EXCEEDED("G01003", "docs.parse.memory.exceeded"),
    /** 文档解析超时 */
    PARSE_TIMEOUT("G01004", "docs.parse.timeout"),
    /** 文档为空或无法读取 */
    DOCUMENT_EMPTY("G01005", "docs.empty"),
    /** 文档已加密，无法解析 */
    DOCUMENT_ENCRYPTED("G01006", "docs.encrypted"),
    /** 文本归一化失败 */
    NORMALIZE_FAILED("G02001", "docs.normalize.failed"),
    /** 文本清洗失败 */
    CLEAN_FAILED("G02002", "docs.clean.failed"),
    /** 文本分块失败 */
    CHUNK_FAILED("G02003", "docs.chunk.failed"),
    /** 安全扫描失败 */
    SECURITY_SCAN_FAILED("G03001", "docs.security.scan.failed"),
    /** 检测到高危安全风险 */
    SECURITY_RISK_DETECTED("G03002", "docs.security.risk.detected"),
    /** PII 检测正则编译失败 */
    PII_PATTERN_COMPILE_FAILED("G04001", "docs.pii.pattern.compile.failed"),
    /** PII 检测异常 */
    PII_DETECTION_FAILED("G04002", "docs.pii.detection.failed"),
    /** PDF 脱敏失败 */
    PDF_REDACT_FAILED("G05001", "docs.pdf.redact.failed"),
    /** 文本脱敏失败 */
    TEXT_REDACT_FAILED("G05002", "docs.text.redact.failed"),
    /** 水印添加失败 */
    WATERMARK_FAILED("G06001", "docs.watermark.failed"),
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
     * <p>该码会随统一响应体返回给前端并写入日志，是跨系统排障的检索键，
     * 一经发布不可修改，废弃时只能新增码值而非复用旧码。
     * 段位含义见类级注释中的 G01~G99 分区说明。
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
     * <p>由全局异常处理器拿该键去 {@code MessageSource} 查找对应语言的提示文案，
     * 从而实现错误提示与错误码解耦。若资源文件中缺失该键，
     * 框架通常降级为直接展示键名本身，因此新增枚举项时必须同步补齐 i18n 配置。
     *
     * @return 形如 {@code docs.parse.failed} 的点分消息键；恒不为 {@code null}
     */
    @Override
    public String getKey() {
        return key;
    }
}
