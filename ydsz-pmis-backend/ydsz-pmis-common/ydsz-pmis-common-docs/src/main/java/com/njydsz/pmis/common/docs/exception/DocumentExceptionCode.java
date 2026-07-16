package com.njydsz.pmis.common.docs.exception;

import com.njydsz.pmis.common.exception.enums.ExceptionCode;

/**
 * 文档处理模块异常码枚举
 * <p>
 * 采用两段式错误码结构（D 段位 + 五位数字），便于按域分类与日志检索：
 * <ul>
 *   <li>D01*** - 解析错误（不支持格式/解析失败/内存超限等）</li>
 *   <li>D02*** - 预处理错误（归一化/清洗/分块失败）</li>
 *   <li>D03*** - 安全扫描错误（宏检测/PDF JS 检测失败）</li>
 *   <li>D04*** - PII 检测错误（正则编译失败/检测异常）</li>
 *   <li>D05*** - 脱敏错误（PDF 脱敏/文本脱敏失败）</li>
 *   <li>D06*** - 水印错误（水印添加失败）</li>
 *   <li>D07*** - 转换错误（格式转换失败）</li>
 *   <li>D99*** - 未知错误（兜底）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DocumentExceptionCode implements ExceptionCode {

    /** 不支持的文档格式 */
    UNSUPPORTED_FORMAT("D01001", "docs.format.unsupported"),
    /** 文档解析失败 */
    PARSE_FAILED("D01002", "docs.parse.failed"),
    /** 文档解析内存超限 */
    PARSE_MEMORY_EXCEEDED("D01003", "docs.parse.memory.exceeded"),
    /** 文档解析超时 */
    PARSE_TIMEOUT("D01004", "docs.parse.timeout"),
    /** 文档为空或无法读取 */
    DOCUMENT_EMPTY("D01005", "docs.empty"),
    /** 文档已加密，无法解析 */
    DOCUMENT_ENCRYPTED("D01006", "docs.encrypted"),
    /** 文本归一化失败 */
    NORMALIZE_FAILED("D02001", "docs.normalize.failed"),
    /** 文本清洗失败 */
    CLEAN_FAILED("D02002", "docs.clean.failed"),
    /** 文本分块失败 */
    CHUNK_FAILED("D02003", "docs.chunk.failed"),
    /** 安全扫描失败 */
    SECURITY_SCAN_FAILED("D03001", "docs.security.scan.failed"),
    /** 检测到高危安全风险 */
    SECURITY_RISK_DETECTED("D03002", "docs.security.risk.detected"),
    /** PII 检测正则编译失败 */
    PII_PATTERN_COMPILE_FAILED("D04001", "docs.pii.pattern.compile.failed"),
    /** PII 检测异常 */
    PII_DETECTION_FAILED("D04002", "docs.pii.detection.failed"),
    /** PDF 脱敏失败 */
    PDF_REDACT_FAILED("D05001", "docs.pdf.redact.failed"),
    /** 文本脱敏失败 */
    TEXT_REDACT_FAILED("D05002", "docs.text.redact.failed"),
    /** 水印添加失败 */
    WATERMARK_FAILED("D06001", "docs.watermark.failed"),
    /** 格式转换失败 */
    CONVERT_FAILED("D07001", "docs.convert.failed"),
    /** 未知错误（兜底） */
    UNKNOWN("D99999", "unknown.error");

    private final String code;
    private final String key;

    DocumentExceptionCode(String code, String key) {
        this.code = code;
        this.key = key;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getKey() {
        return key;
    }
}
