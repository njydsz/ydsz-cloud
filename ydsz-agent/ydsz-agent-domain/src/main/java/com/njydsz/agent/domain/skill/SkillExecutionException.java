package com.njydsz.agent.domain.skill;

import com.njydsz.common.exception.custom.BusinessException;

/**
 * Skill 执行异常
 *
 * <p>封装 Skill 执行过程中的失败场景：执行超时、沙箱初始化失败、
 * 脚本不存在、资源限制等。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public class SkillExecutionException extends BusinessException {

  private static final long serialVersionUID = 1L;

  /** 错误码 */
  private static final String ERROR_CODE = "SKILL_EXECUTION_ERROR";

  /** Skill 编码 */
  private final String skillCode;

  /**
   * 构造 Skill 执行异常。
   *
   * @param skillCode Skill 编码
   * @param key       国际化消息键
   */
  public SkillExecutionException(String skillCode, String key) {
    super(ERROR_CODE, key);
    this.skillCode = skillCode;
  }

  /**
   * 构造 Skill 执行异常（含根因）。
   *
   * <p>由于父类 BusinessException 不支持同时指定 code + key + cause，
   * 此构造器将 key 作为 message 传递，通过 {@link #getSkillCode()} 补充上下文。
   *
   * @param skillCode Skill 编码
   * @param message   异常描述
   * @param cause     根因
   */
  public SkillExecutionException(String skillCode, String message, Throwable cause) {
    super(message, cause);
    this.skillCode = skillCode;
  }

  public String getSkillCode() {
    return skillCode;
  }
}
