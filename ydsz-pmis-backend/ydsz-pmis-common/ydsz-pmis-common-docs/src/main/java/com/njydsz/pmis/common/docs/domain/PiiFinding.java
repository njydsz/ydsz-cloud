package com.njydsz.pmis.common.docs.domain;

import com.njydsz.pmis.common.docs.enums.PiiType;
import lombok.Builder;
import lombok.Data;

/**
 * PII 发现结果
 * <p>
 * 表示在文档中检测到的敏感信息项。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 1.0.0
 * @since 1.3.0
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
