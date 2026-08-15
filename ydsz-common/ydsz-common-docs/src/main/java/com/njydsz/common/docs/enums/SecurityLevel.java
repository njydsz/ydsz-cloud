package com.njydsz.common.docs.enums;

/**
 * 文档安全等级
 * <p>
 * 表示文档安全扫描结果的严重程度。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum SecurityLevel {

    /** 安全：未检测到任何安全风险 */
    SAFE,
    /** 低风险：检测到外部链接等轻微风险项 */
    LOW,
    /** 中风险：检测到 PDF JavaScript 或可疑嵌入对象 */
    MEDIUM,
    /** 高风险：检测到 Office 宏或已知恶意特征 */
    HIGH,
    /** 严重：检测到多种高危风险项，建议直接拒绝 */
    CRITICAL
}
