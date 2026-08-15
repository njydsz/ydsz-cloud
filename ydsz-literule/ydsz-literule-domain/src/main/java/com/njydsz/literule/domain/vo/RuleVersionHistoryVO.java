package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则版本历史视图对象（VO）。
 * <p>
 * 用于 Controller 层返回规则版本变更记录，包含版本号、定义快照、
 * 变更说明及操作人，支撑规则的版本回溯与差异比对。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionHistoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 版本记录唯一标识（主键） */
    private String id;
    /** 规则编码 */
    private String ruleCode;
    /** 版本号 */
    private Integer version;
    /** 该版本的规则定义 JSON 快照 */
    private String definitionJson;
    /** 变更说明 */
    private String changeDesc;
    /** 操作人 */
    private String operator;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
