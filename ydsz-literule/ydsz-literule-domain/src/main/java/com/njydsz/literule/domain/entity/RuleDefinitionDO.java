package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.literule.domain.enums.RuleStatusEnum;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * LiteRule 规则定义 DO
 *
 * <p>映射 ydsz_rule_def 表，存储可配置规则的全部元信息。 封装规则生命周期状态流转行为，避免逻辑散落在 Service 层。
 *
 * <p><b>状态流转：</b>
 *
 * <pre>
 *   DRAFT ──publish()──▶ PUBLISHED ──disable()──▶ DISABLED
 *     ▲                     │                        │
 *     └────revertToDraft()──┴────────────────────────┘
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_def")
public class RuleDefinitionDO extends MpBaseEntity<String> {

  /** 规则编码，业务唯一 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 规则分类编码 */
  private String category;

  /**
   * 分类路径（P1-9 规则目录树）
   *
   * <p>用 {@code /} 分隔的多级分类，如 {@code "finance/credit/loan"}。 与 category 字段的关系：category
   * 保留作为一级分类标识（兼容老数据/快捷筛选）， categoryPath 是规则在目录树中的完整路径（用于左侧树形导航）。
   */
  private String categoryPath;

  /**
   * 责任人（P1-9 规则目录树）
   *
   * <p>规则负责人工号/用户名。Owner 在以下场景使用：
   *
   * <ul>
   *   <li>规则异常告警通知（如执行失败率突增）
   *   <li>AB Test 自动回滚后的通知
   *   <li>规则审核/巡检派单
   * </ul>
   */
  private String owner;

  /** 规则描述 */
  private String description;

  /** 条件表达式（LiteExpr 语法） */
  private String conditionExpression;

  /** 严重度表达式，可选 */
  private String severityExpression;

  /** 默认严重级别 */
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

  /**
   * 互斥组名称（同组内首个命中后跳过其余规则；null 表示无互斥组）
   *
   * @since 1.0.0
   */
  private String mutexGroup;

  /** 是否支持下钻查看详情 */
  private Boolean drilldownAvailable;

  /**
   * 乐观锁版本号
   *
   * <p>并发更新规则时防止覆盖：UPDATE 自动追加 {@code WHERE version = #{oldVersion}}， 若记录已被其他事务修改，UPDATE 影响行数为
   * 0，业务层应据此抛出乐观锁冲突异常。 SQL DDL：{@code version INTEGER NOT NULL DEFAULT 1}。
   */
  @Version private Integer version;

  /**
   * 生命周期状态
   *
   * <p>数据库存储为字符串，通过 {@link #getStatusEnum()} / {@link #setStatusEnum(RuleStatusEnum)} 提供枚举视图。
   */
  private String status;

  /** 生效时间 */
  private LocalDateTime effectiveFrom;

  /** 失效时间 */
  private LocalDateTime effectiveTo;

  /** 审核人 */
  private String reviewedBy;

  /** 审核时间 */
  private LocalDateTime reviewedAt;

  /** 审核意见 */
  private String reviewComment;

  /** 灰度比例（0.0~1.0，0 表示不启用灰度；P1-10 AB Test 自动回滚用） */
  private Double canaryRatio;

  /** 灰度条件表达式列表（JSON 数组） */
  private String canaryConditions;

  /** 灰度候选版本条件表达式 */
  private String canaryConditionExpression;

  /** 灰度候选版本严重度表达式 */
  private String canarySeverityExpression;

  // ==================== 领域行为方法 ====================

  /**
   * 获取状态枚举视图。
   *
   * @return 状态枚举，无法解析时返回 null
   */
  @TableField(exist = false)
  public RuleStatusEnum getStatusEnum() {
    return RuleStatusEnum.parse(this.status);
  }

  /**
   * 设置状态（通过枚举）。
   *
   * @param statusEnum 状态枚举，不可为 null
   */
  public void setStatusEnum(RuleStatusEnum statusEnum) {
    if (statusEnum == null) {
      throw new IllegalArgumentException("规则状态不可为 null");
    }
    this.status = statusEnum.name();
  }

  /**
   * 发布规则。
   *
   * <p>仅允许从 DRAFT 或 DISABLED 状态发布。发布后规则参与匹配。
   *
   * @param reviewer 审核人
   * @throws IllegalStateException 当当前状态不允许发布时
   */
  public void publish(String reviewer) {
    RuleStatusEnum current = getStatusEnum();
    if (current == null) {
      current = RuleStatusEnum.DRAFT;
    }
    if (!current.canTransitTo(RuleStatusEnum.PUBLISHED)) {
      throw new IllegalStateException(String.format("规则[%s]当前状态[%s]不允许发布", ruleCode, current));
    }
    this.status = RuleStatusEnum.PUBLISHED.name();
    this.reviewedBy = reviewer;
    this.reviewedAt = LocalDateTime.now();
  }

  /**
   * 停用规则。
   *
   * <p>停用后规则不再参与匹配，但可重新编辑或发布。
   *
   * @throws IllegalStateException 当当前状态不允许停用时
   */
  public void disable() {
    RuleStatusEnum current = getStatusEnum();
    if (current == null) {
      throw new IllegalStateException(String.format("规则[%s]状态为空，无法停用", ruleCode));
    }
    if (!current.canTransitTo(RuleStatusEnum.DISABLED)) {
      throw new IllegalStateException(String.format("规则[%s]当前状态[%s]不允许停用", ruleCode, current));
    }
    this.status = RuleStatusEnum.DISABLED.name();
  }

  /**
   * 回退到草稿状态（重新编辑）。
   *
   * <p>从 PUBLISHED 或 DISABLED 状态回退到 DRAFT，清空审核信息。
   *
   * @throws IllegalStateException 当当前状态不允许回退时
   */
  public void revertToDraft() {
    RuleStatusEnum current = getStatusEnum();
    if (current == null) {
      throw new IllegalStateException(String.format("规则[%s]状态为空，无法回退", ruleCode));
    }
    if (!current.canTransitTo(RuleStatusEnum.DRAFT)) {
      throw new IllegalStateException(String.format("规则[%s]当前状态[%s]不允许回退到草稿", ruleCode, current));
    }
    this.status = RuleStatusEnum.DRAFT.name();
    this.reviewedBy = null;
    this.reviewedAt = null;
    this.reviewComment = null;
  }

  /**
   * 判断规则是否可编辑。
   *
   * <p>DRAFT 和 DISABLED 状态下的规则允许编辑。
   *
   * @return true 表示可编辑
   */
  public boolean isEditable() {
    RuleStatusEnum current = getStatusEnum();
    return current == RuleStatusEnum.DRAFT || current == RuleStatusEnum.DISABLED;
  }

  /**
   * 判断规则是否处于生效窗口内。
   *
   * <p>检查当前时间是否在 {@link #effectiveFrom} 和 {@link #effectiveTo} 之间。 边界情况：effectiveFrom 为 null
   * 表示立即生效，effectiveTo 为 null 表示永不过期。
   *
   * @param now 当前时间
   * @return true 表示在生效窗口内
   */
  public boolean isInEffectiveWindow(LocalDateTime now) {
    if (now == null) {
      return true;
    }
    if (effectiveFrom != null && now.isBefore(effectiveFrom)) {
      return false;
    }
    if (effectiveTo != null && now.isAfter(effectiveTo)) {
      return false;
    }
    return true;
  }

  /**
   * 判断规则是否已发布且启用。
   *
   * @return true 表示已发布且启用
   */
  public boolean isActive() {
    return RuleStatusEnum.PUBLISHED.name().equals(status) && Boolean.TRUE.equals(enabled);
  }

  /**
   * 判断是否启用了灰度。
   *
   * @return true 表示启用了灰度发布
   */
  public boolean isCanaryEnabled() {
    return canaryRatio != null && canaryRatio > 0.0;
  }
}
