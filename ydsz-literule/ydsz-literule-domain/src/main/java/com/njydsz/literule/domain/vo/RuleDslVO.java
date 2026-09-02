package com.njydsz.literule.domain.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 规则 DSL（领域特定语言）视图对象（VO）。
 *
 * <p>用于承载一次 DSL 导入/导出解析后的结构，包含规则定义列表、规则链列表及元信息。 DSL 以文本化的方式批量描述规则与编排，便于版本管理与跨环境迁移。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class RuleDslVO {

  /** DSL 中定义的规则列表（每项为一个规则定义对象） */
  private List<Object> rules;

  /** DSL 中定义的规则链列表（每项为一个链编排对象） */
  private List<Object> chains;

  /** DSL 元信息（如版本、作者、来源等键值对） */
  private Map<String, Object> meta;
}
