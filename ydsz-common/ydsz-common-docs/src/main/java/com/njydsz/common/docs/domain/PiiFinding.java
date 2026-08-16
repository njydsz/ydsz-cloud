package com.njydsz.common.docs.domain;

import lombok.Builder;
import lombok.Data;

import com.njydsz.common.docs.enums.PiiType;

/**
 * PII 发现结果
 * <p>
 * 表示在文档中检测到的敏感信息项。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class PiiFinding {

    /** PII 类型 */
    private PiiType type;

    /** 匹配到的原文（已脱敏，如 138****1234） */
    private String maskedValue;

    /** 在全文中的起始位置 */
    private int startIndex;

    /** 在全文中的结束位置 */
    private int endIndex;

    /** 所在页码（如可确定） */
    private Integer pageNumber;

    /** 置信度（0-1） */
    private double confidence;
}
