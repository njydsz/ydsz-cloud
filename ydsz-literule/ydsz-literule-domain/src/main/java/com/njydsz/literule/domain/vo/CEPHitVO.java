package com.njydsz.literule.domain.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * CEP（复杂事件处理）命中视图对象（VO）。
 *
 * <p>用于前端展示某条 CEP 模式被事件流命中的记录， 包含命中的模式、规则、匹配到的事件及命中时的度量值。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class CEPHitVO {

  /** 命中的 CEP 模式 ID */
  private String patternId;

  /** 关联规则编码 */
  private String ruleCode;

  /** 命中的事件列表（按模式匹配到的原始/派生事件对象） */
  private List<Object> matchedEvents;

  /** 命中时间（Instant，事件流中的时间戳） */
  private Instant hitAt;

  /** 命中指标值（如窗口内聚合度量，用于排序/告警分级） */
  private double metric;

  /** 命中上下文（附加维度信息，如项目/组织等） */
  private Map<String, Object> context;
}
