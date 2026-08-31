package com.njydsz.literule.domain.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.vo.RuleContext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则定义（元数据）
 *
 * <p>描述一条可配置规则的完整元信息，支持从数据库加载或编程式创建。 conditionExpression 为 LiteExpr 表达式，返回 boolean；actionExpression
 * 可选，用于动态生成结果描述。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String code;

  /** 规则名称 */
  private String name;

  /** 规则类别 */
  private String category;

  /**
   * 分类路径（P1-9 规则目录树）
   *
   * <p>多级分类用 {@code /} 分隔，如 {@code "finance/credit/loan"}。前端左侧树按此字段构建。 兼容：category
   * 保留作为一级分类，categoryPath 可空（空时按 category 显示）。
   */
  private String categoryPath;

  /**
   * 责任人（P1-9 规则目录树）
   *
   * <p>工号/用户名。Owner 在以下场景使用：
   *
   * <ul>
   *   <li>规则异常告警通知（执行失败率突增、连续 N 次未命中）
   *   <li>AB Test 自动回滚后的通知
   *   <li>规则巡检/审核派单
   * </ul>
   */
  private String owner;

  /** 规则描述 */
  private String description;

  /**
   * 条件表达式（LiteExpr 语法）
   *
   * <p>示例：{@code evmRedCount >= 3} 或 {@code grossMargin < 0.05 && confirmedRevenue > 0}
   */
  private String conditionExpression;

  /**
   * 严重度表达式（LiteExpr 语法，可选）
   *
   * <p>当条件满足时，根据上下文动态决定严重度。 示例：{@code benchIdleCost >= 1000000 ? 'RED' : 'YELLOW'} 为空时使用 {@link
   * #defaultSeverity}
   */
  private String severityExpression;

  /** 默认严重度（当 severityExpression 为空时使用） */
  private RuleSeverity defaultSeverity;

  /** 标题模板（支持 ${var} 占位符） */
  private String titleTemplate;

  /** 描述模板（支持 ${var} 占位符） */
  private String descriptionTemplate;

  /** 优先级（数值越小越先执行） */
  @Builder.Default private int priority = com.njydsz.literule.domain.Rule.DEFAULT_PRIORITY;

  /** 是否启用 */
  @Builder.Default private boolean enabled = true;

  /** 影响范围 */
  private String scope;

  /**
   * 互斥组名称
   *
   * <p>同组内首个命中的规则执行后，其余规则跳过评估。null 表示无互斥组。
   *
   * @since 1.0.0
   */
  private String mutexGroup;

  /** 是否可下钻 */
  @Builder.Default private boolean drilldownAvailable = true;

  /** 当前版本号 */
  @Builder.Default private int version = 1;

  /**
   * 租户 ID
   *
   * <p>多租户隔离标识，单租户部署下默认为 1。 1.5.0 起启用运行时租户过滤：{@link
   * com.njydsz.literule.server.core.DefaultRuleEngine} 在评估前会比较 {@code rule.getTenantId()} 与 {@link
   * RuleContext#getTenantId()}， 仅当两者匹配时才评估该规则。
   *
   * @since 1.0.0
   */
  @Builder.Default private String tenantId = "1";

  /**
   * 环境标识（dev/staging/prod/default）
   *
   * <p>与 {@link #tenantId} 正交，实现多环境规则隔离（P1-5）。
   *
   * <ul>
   *   <li>{@code "default"}（默认）- 全环境生效，向后兼容
   *   <li>{@code "dev"} / {@code "staging"} / {@code "prod"} - 仅匹配同环境的上下文
   * </ul>
   *
   * 过滤规则：规则的 environment 为 {@code "default"} 时匹配任何上下文环境； 非 {@code "default"} 时必须与 {@link
   * RuleContext#getEnvironment()} 完全匹配。
   *
   * @since 1.0.0
   */
  @Builder.Default private String environment = "default";

  /** 生命周期状态 */
  @Builder.Default private String status = "PUBLISHED";

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

  /**
   * 灰度比例（0.0~1.0，0 表示不启用灰度）
   *
   * <p>当 canaryRatio > 0 且存在候选版本（canaryDefinition 非空）时， 引擎按此比例将流量分到候选版本。
   *
   * @since 1.0.0
   */
  @Builder.Default private double canaryRatio = 0.0;

  /**
   * 灰度条件（LiteExpr 表达式列表，AND 关系）
   *
   * <p>仅当 canaryRatio > 0 时生效；满足全部条件才进入灰度流量分桶。 示例：{@code ["tenantId == 'T001'", "userRole ==
   * 'ADMIN'"]} 为空时仅按 canaryRatio 比例分桶。
   *
   * @since 1.0.0
   */
  private List<String> canaryConditions;

  /**
   * 灰度候选版本表达式（条件/严重度表达式，覆盖主版本）
   *
   * <p>当流量被分到灰度桶时，使用此候选表达式构造一条临时规则进行评估， 结果会被标记 {@link com.njydsz.literule.domain.vo.RuleResult#isCanary()} = true，便于运营对比新旧命中差异。
   *
   * @since 1.0.0
   */
  private String canaryConditionExpression;

  /** 灰度候选版本的严重度表达式 */
  private String canarySeverityExpression;
}
