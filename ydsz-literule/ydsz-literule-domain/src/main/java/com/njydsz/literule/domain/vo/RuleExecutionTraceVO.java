package com.njydsz.literule.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

/**
 * 规则执行轨迹视图对象（VO）。
 *
 * <p>用于 Controller 层返回单次规则执行的完整轨迹信息，包含命中结果、 严重级别、条件求值结果、耗时及错误信息，支撑执行回放和问题排查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleExecutionTraceVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 轨迹记录唯一标识（主键） */
  private String id;

  /** 链路追踪 ID，用于关联同一次请求的多条轨迹 */
  private String traceId;

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 执行场景标识 */
  private String scenario;

  /** 是否命中触发 */
  private Boolean triggered;

  /** 命中严重级别（HIGH/MEDIUM/LOW/INFO） */
  private String severity;

  /** 条件表达式求值结果 */
  private String conditionResult;

  /** 执行耗时（毫秒） */
  private Long elapsedMs;

  /** 错误信息（执行异常时填充） */
  private String errorMessage;

  /** 事实数据快照（用于执行回放） */
  private Map<String, Object> factsSnapshot;

  /** 结果快照 */
  private Map<String, Object> resultSnapshot;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
