package com.njydsz.literule.domain.enums;

import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 轻量规则引擎模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}， 支持
 * i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 *
 * <ul>
 *   <li>B93001-B93099 规则定义
 *   <li>B93101-B93199 规则包/版本
 *   <li>B93201-B93299 规则链/决策表
 *   <li>B93301-B93399 测试用例/DSL
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@YdszExceptionCode(module = "literule", description = "规则引擎")
public enum LiteruleExceptionCode implements ExceptionCode {

  // ==================== B93001-B93099 规则定义 ====================

  /** 规则不存在 */
  RULE_NOT_FOUND("B93001", "literule.rule.not.found", 404),

  /** 规则编码重复 */
  RULE_CODE_DUPLICATE("B93002", "literule.rule.code.duplicate"),

  /** 规则表达式非法 */
  RULE_EXPRESSION_INVALID("B93003", "literule.rule.expression.invalid"),

  /** 规则状态非法 */
  RULE_STATUS_INVALID("B93004", "literule.rule.status.invalid"),

  /** 规则状态迁移非法 */
  RULE_STATUS_TRANSITION_ILLEGAL("B93005", "literule.rule.status.transition.illegal"),

  // ==================== B93101-B93199 规则包/版本 ====================

  /** 规则包不存在 */
  RULE_PACK_NOT_FOUND("B93101", "literule.rule.pack.not.found", 404),

  /** 规则版本不存在 */
  RULE_VERSION_NOT_FOUND("B93102", "literule.rule.version.not.found", 404),

  /** 规则包已安装 */
  RULE_PACK_ALREADY_INSTALLED("B93103", "literule.rule.pack.already.installed"),

  // ==================== B93201-B93299 规则链/决策表 ====================

  /** 规则链不存在 */
  RULE_CHAIN_NOT_FOUND("B93201", "literule.rule.chain.not.found", 404),

  /** 决策表不存在 */
  DECISION_TABLE_NOT_FOUND("B93202", "literule.decision.table.not.found", 404),

  /** AB 策略不存在 */
  AB_POLICY_NOT_FOUND("B93203", "literule.ab.policy.not.found", 404),

  // ==================== B93301-B93399 测试用例/DSL ====================

  /** 测试用例不存在 */
  TEST_CASE_NOT_FOUND("B93301", "literule.test.case.not.found", 404),

  /** DSL 解析错误 */
  DSL_PARSE_ERROR("B93302", "literule.dsl.parse.error"),

  /** 变量定义不存在 */
  VARIABLE_DEF_NOT_FOUND("B93303", "literule.variable.def.not.found", 404),

  // ==================== B93401-B93499 模型调用 ====================

  /** 模型调用错误 */
  MODEL_INVOCATION_ERROR("B93401", "literule.model.invocation.error"),

  // ==================== B93501-B93599 运行时/锁/审批/安全/基础设施 ====================

  /** 审批流编码必填 */
  APPROVAL_FLOW_CODE_REQUIRED("B93501", "literule.approval.flow.code.required"),

  /** 审批步骤不存在 */
  APPROVAL_STEP_NOT_FOUND("B93502", "literule.approval.step.not.found", 404),

  /** 审批记录不存在 */
  APPROVAL_RECORD_NOT_FOUND("B93503", "literule.approval.record.not.found", 404),

  /** 审批非法状态流转 */
  APPROVAL_STATE_TRANSITION_DENIED("B93504", "literule.approval.state.transition.denied"),

  /** 审批流程已禁用 */
  APPROVAL_FLOW_DISABLED("B93505", "literule.approval.flow.disabled"),

  /** 分布式锁获取失败（超时/中断），属于基础设施异常 */
  LOCK_ACQUIRE_FAILED("B93510", "literule.lock.acquire.failed", 503),

  /** 锁中断 */
  LOCK_INTERRUPTED("B93511", "literule.lock.interrupted", 503),

  /** 脚本编译失败 */
  SCRIPT_COMPILATION_FAILED("B93512", "literule.script.compilation.failed"),

  /** DSL 规则引擎字节码非法 */
  ENGINE_BYTECODE_INVALID("B93513", "literule.engine.bytecode.invalid"),

  /** 决策表编码必填 */
  DECISION_TABLE_CODE_REQUIRED("B93514", "literule.decision.table.code.required"),

  /** 决策表 Excel 非法 */
  DECISION_TABLE_EXCEL_INVALID("B93515", "literule.decision.table.excel.invalid"),

  /** Excel 导入/导出失败 */
  EXCEL_IMPORT_FAILED("B93516", "literule.excel.import.failed", 500),

  /** 规则链存在循环 */
  RULE_CHAIN_CYCLE_DETECTED("B93517", "literule.rule.chain.cycle.detected"),

  /** 租户隔离校验失败 */
  TENANT_ISOLATION_REQUIRED("B93518", "literule.tenant.isolation.required", 403),

  // ==================== B93601-B93699 安全/沙箱/熔断 ====================

  /** 审批越权操作 */
  SECURITY_PRIVILEGE_ESCALATION("B93601", "literule.security.privilege.escalation", 403),

  /** 沙箱调用违规 */
  SECURITY_SANDBOX_VIOLATION("B93602", "literule.security.sandbox.violation", 403),

  /** 熔断器配置非法 */
  CIRCUIT_BREAKER_INVALID_CONFIG("B93603", "literule.circuit.breaker.invalid.config", 500);

  /** 默认 HTTP 状态码（业务参数错误） */
  private static final int DEFAULT_HTTP_STATUS = 400;

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  LiteruleExceptionCode(String code, String key) {
    this(code, key, DEFAULT_HTTP_STATUS);
  }

  LiteruleExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
