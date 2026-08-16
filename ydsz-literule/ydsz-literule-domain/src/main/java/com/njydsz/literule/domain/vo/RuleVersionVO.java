package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 规则版本视图对象（VO）。
 *
 * <p>用于前端展示规则单次版本快照的信息，包含版本号、定义 JSON、变更说明与操作人，
 * 支撑版本查看与回溯。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleVersionVO {

    /** 版本记录唯一标识（主键） */
    private String id;

    /** 规则编码 */
    private String ruleCode;

    /** 版本号 */
    private int version;

    /** 该版本的规则定义 JSON 快照 */
    private String definitionJson;

    /** 变更说明 */
    private String changeDesc;

    /** 操作人 */
    private String operator;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
