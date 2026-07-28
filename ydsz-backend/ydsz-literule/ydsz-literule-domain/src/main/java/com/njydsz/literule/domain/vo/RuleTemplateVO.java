package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 规则模板视图对象（VO）。
 * <p>
 * 用于 Controller 层返回规则模板的完整信息。规则模板预置条件表达式、
 * 严重度表达式和告警模板，用户基于模板快速创建规则，按行业和标签分类管理。
 * </p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleTemplateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板唯一标识（主键） */
    private String id;
    /** 模板编码，业务唯一 */
    private String templateCode;
    /** 模板名称 */
    private String templateName;
    /** 分类编码 */
    private String category;
    /** 模板描述 */
    private String description;
    /** 预置条件表达式 */
    private String conditionExpression;
    /** 预置严重度表达式 */
    private String severityExpression;
    /** 默认严重级别 */
    private String defaultSeverity;
    /** 告警标题模板 */
    private String titleTemplate;
    /** 告警描述模板 */
    private String descriptionTemplate;
    /** 优先级，数值越小优先级越高 */
    private Integer priority;
    /** 适用范围 */
    private String scope;
    /** 所属行业 */
    private String industry;
    /** 标签，逗号分隔 */
    private String tags;
    /** 创建人 */
    private String createdBy;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新人 */
    private String updatedBy;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}
