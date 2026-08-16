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
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "literule", description = "规则引擎")
public enum LiteruleExceptionCode implements ExceptionCode {

  // ==================== B93001-B93099 规则定义 ====================
  RULE_NOT_FOUND("B93001", "literule.rule.not.found", 404),
  RULE_CODE_DUPLICATE("B93002", "literule.rule.code.duplicate"),
  RULE_EXPRESSION_INVALID("B93003", "literule.rule.expression.invalid"),
  RULE_STATUS_INVALID("B93004", "literule.rule.status.invalid"),

  // ==================== B93101-B93199 规则包/版本 ====================
  RULE_PACK_NOT_FOUND("B93101", "literule.rule.pack.not.found", 404),
  RULE_VERSION_NOT_FOUND("B93102", "literule.rule.version.not.found", 404),
  RULE_PACK_ALREADY_INSTALLED("B93103", "literule.rule.pack.already.installed"),

  // ==================== B93201-B93299 规则链/决策表 ====================
  RULE_CHAIN_NOT_FOUND("B93201", "literule.rule.chain.not.found", 404),
  DECISION_TABLE_NOT_FOUND("B93202", "literule.decision.table.not.found", 404),
  AB_POLICY_NOT_FOUND("B93203", "literule.ab.policy.not.found", 404),

  // ==================== B93301-B93399 测试用例/DSL ====================
  TEST_CASE_NOT_FOUND("B93301", "literule.test.case.not.found", 404),
  DSL_PARSE_ERROR("B93302", "literule.dsl.parse.error"),
  VARIABLE_DEF_NOT_FOUND("B93303", "literule.variable.def.not.found", 404);

  /** 错误码 */
  private final String code;

  /** 国际化消息键 */
  private final String key;

  /** HTTP 状态码 */
  private final int httpStatus;

  LiteruleExceptionCode(String code, String key) {
    this(code, key, 400);
  }

  LiteruleExceptionCode(String code, String key, int httpStatus) {
    this.code = code;
    this.key = key;
    this.httpStatus = httpStatus;
  }
}
