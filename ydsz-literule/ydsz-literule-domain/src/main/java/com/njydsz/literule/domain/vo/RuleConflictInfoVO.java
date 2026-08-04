package com.njydsz.literule.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 规则冲突信息视图对象（VO）。
 *
 * <p>用于前端展示规则冲突检测结果：当两条规则在相同或相关字段上可能产生
 * 相互矛盾的判定时，标记为冲突，并列出重叠字段与严重级别，辅助梳理规则集。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleConflictInfoVO {

    /** 冲突方规则 A 的编码 */
    private String ruleA;

    /** 冲突方规则 A 的名称（展示用） */
    private String ruleAName;

    /** 冲突方规则 B 的编码 */
    private String ruleB;

    /** 冲突方规则 B 的名称（展示用） */
    private String ruleBName;

    /** 两条规则重叠/冲突的字段名列表（如预算金额、进度等） */
    private List<String> overlapFields;

    /** 冲突严重级别（如 HIGH/MEDIUM/LOW） */
    private String severity;

}
