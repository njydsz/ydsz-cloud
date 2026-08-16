package com.njydsz.literule.server.spi;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 规则模板元数据
 *
 * <p>规则模板市场（{@code ydsz_rule_template}）中预置模板的只读视图， 供 literule 模块通过 {@link RuleTemplateProvider}
 * 暴露给消费方。
 *
 * <p>与持久层 {@code RuleTemplate} 解耦：
 *
 * <ul>
 *   <li>剥离 {@code id} / {@code createdBy} / {@code createdAt} 等审计字段
 *   <li>剥离 {@code priority} / {@code scope} / {@code titleTemplate} / {@code descriptionTemplate}
 *       等运行时字段
 *   <li>{@code tags} 由逗号分隔字符串转为 {@link List}，便于前端渲染
 *   <li>新增 {@code usageCount} 反映模板被引用次数，用于市场排序
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@Builder
public class RuleTemplateMeta implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 模板编码（唯一） */
  private String templateCode;

  /** 模板名称 */
  private String templateName;

  /** 模板类别（如 FINANCE / EVM / BENCH） */
  private String category;

  /** 适用行业编码 */
  private String industry;

  /** 模板描述 */
  private String description;

  /** 条件表达式模板（LiteExpr 语法） */
  private String conditionTemplate;

  /** 严重度表达式模板（LiteExpr 语法，可选） */
  private String severityTemplate;

  /** 默认严重度编码（RED / YELLOW / INFO / GREEN） */
  private String defaultSeverity;

  /** 标签列表（用于市场筛选与检索） */
  private List<String> tags;

  /** 被引用次数（用于市场热度排序） */
  private long usageCount;
}
