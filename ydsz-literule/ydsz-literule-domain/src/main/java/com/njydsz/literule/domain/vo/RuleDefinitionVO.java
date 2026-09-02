package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 规则定义视图对象（VO）。
 *
 * <p>用于 Controller 层返回规则定义的完整信息，涵盖规则基本信息、条件表达式、 灰度发布配置、审批信息及审计字段。不参与持久化，仅用于展示和传输。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleDefinitionVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则唯一标识（主键） */
  private String id;

  /** 规则编码，业务唯一，用于规则引用和路由 */
  private String ruleCode;

  /** 规则名称，用于展示 */
  private String ruleName;

  /** 规则分类编码 */
  private String category;

  /** 分类完整路径，如 "risk/credit/loan" */
  private String categoryPath;

  /** 规则归属人 */
  private String owner;

  /** 规则描述 */
  private String description;

  /** 条件表达式（LiteExpr 语法） */
  private String conditionExpression;

  /** 严重度表达式，动态计算规则命中后的严重级别 */
  private String severityExpression;

  /** 默认严重级别（HIGH/MEDIUM/LOW/INFO） */
  private String defaultSeverity;

  /** 告警标题模板 */
  private String titleTemplate;

  /** 告警描述模板 */
  private String descriptionTemplate;

  /** 优先级，数值越小优先级越高 */
  private Integer priority;

  /** 是否启用 */
  private Boolean enabled;

  /** 适用范围 */
  private String scope;

  /** 互斥组，同组规则仅命中一条 */
  private String mutexGroup;

  /** 是否支持下钻查看详情 */
  private Boolean drilldownAvailable;

  /** 版本号 */
  private Integer version;

  /** 状态（DRAFT/PENDING_REVIEW/APPROVED/PUBLISHED/REJECTED） */
  private String status;

  /** 租户 ID（多租户隔离，供搜索索引与权限过滤使用） */
  private String tenantId;

  /** 生效起始时间 */
  private LocalDateTime effectiveFrom;

  /** 生效结束时间 */
  private LocalDateTime effectiveTo;

  /** 审批人 */
  private String reviewedBy;

  /** 审批时间 */
  private LocalDateTime reviewedAt;

  /** 审批意见 */
  private String reviewComment;

  /** 灰度发布比例（0.0~1.0），1.0 表示全量发布 */
  private Double canaryRatio;

  /** 灰度条件描述 */
  private String canaryConditions;

  /** 灰度条件表达式 */
  private String canaryConditionExpression;

  /** 灰度严重度表达式 */
  private String canarySeverityExpression;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
