package com.njydsz.literule.infra.entity;

import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import com.njydsz.common.jdbc.handler.JsonTypeHandler;

/**
 * 规则执行链路追踪实体
 *
 * @author ydsz
 * @since 1.0.0
 */
@Data
@SuppressWarnings("unchecked") // @SuperBuilder 生成的代码会触发 unchecked 警告，无法在源码层面修复
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_execution_trace", autoResultMap = true)
public class RuleExecutionTraceVO extends MpBaseIdEntity<String> {

  /** 追踪 ID（同一批次评估共享） */
  private String traceId;

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 业务场景 */
  private String scenario;

  /** 是否触发 */
  private Boolean triggered;

  /** 触发严重度 */
  private String severity;

  /** 条件表达式求值结果描述 */
  private String conditionResult;

  /** 执行耗时（毫秒） */
  private Long elapsedMs;

  /** 事实数据快照 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private Map<String, Object> factsSnapshot;

  /** 结果快照 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private Map<String, Object> resultSnapshot;

  /** 错误信息 */
  private String errorMessage;
}
