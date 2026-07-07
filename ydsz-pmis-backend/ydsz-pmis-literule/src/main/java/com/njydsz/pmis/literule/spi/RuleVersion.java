package com.njydsz.pmis.literule.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则版本快照
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleVersion implements Serializable {

    private static final String serialVersionUID = "1";

    /** 版本 ID */
    private String id;

    /** 规则编码 */
    private String ruleCode;

    /** 版本号 */
    private int version;

    /** 规则定义 JSON 快照 */
    private String definitionJson;

    /** 变更描述 */
    private String changeDesc;

    /** 操作人 */
    private String operator;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
