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
 * 规则执行链路追踪实体。
 *
 * <p>对应 {@code ydsz_rule_execution_trace} 表，逐条记录单次规则评估的过程数据，用于执行结果追溯与问题排查。
 * 同一批次评估共享一个 {@code traceId}，按该字段可还原一次完整评估的调用链。
 *
 * <p>{@code factsSnapshot} 与 {@code resultSnapshot} 为 JSON 列，经 {@link JsonTypeHandler} 序列化，
 * 分别固化评估时的事实输入与规则输出，保证事后可复现当时的求值环境。
 *
 * <p><b>约束：</b>追踪数据只做追加写入，不随规则定义的变更而改写，因此不可用于推断规则当前状态。
 *
 * @author ydsz-team
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
