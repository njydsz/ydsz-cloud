package com.remisoft.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 流程定义视图对象
 *
 * <p>用于 Controller 层返回流程定义数据，对应实体 {@link com.remisoft.workflow.domain.entity.FlowDefinition}。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class FlowDefinitionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 流程编码（业务语义，如 project_initiation） */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 流程分类 */
    private String category;

    /** 流程版本号（如 v1、v2） */
    private String flowVersion;

    /** 设计器模型（CLASSICS 经典 / MIMIC 仿钉钉） */
    private String modelValue;

    /** 审批表单是否自定义（Y/N） */
    private String formCustom;

    /** 表单路径 */
    private String formPath;

    /** 激活状态（0=挂起 / 1=激活） */
    private Integer activityStatus;

    /** 发布状态（0=未发布 / 1=已发布 / 9=失效） */
    private Integer isPublish;

    /** 监听器类型 */
    private String listenerType;

    /** 监听器路径（Spring Bean 路径） */
    private String listenerPath;

    /** 扩展字段（JSON） */
    private String ext;

    /** 流程描述 */
    private String description;

    /** 外部追踪 ID */
    private String providerTraceId;

    /** 灰度发布百分比 */
    private Integer canaryPercent;

    /** 灰度状态 */
    private String canaryStatus;

    /** 灰度策略 */
    private String canaryStrategy;

    /** 灰度发布日志 */
    private String canaryRolloutLog;

    /** 锁定人 */
    private String lockedBy;

    /** 锁定时间 */
    private LocalDateTime lockedAt;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}