package com.njydsz.agent.domain.skill;

import com.njydsz.common.exception.BizException;

/**
 * Skill 执行异常
 *
 * <p>封装 Skill 执行过程中的失败场景：执行超时、沙箱初始化失败、
 * 脚本不存在、资源限制等。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public class SkillExecutionException extends BizException {

  private static final long serialVersionUID = 1L;

  /** Skill 编码 */
  private final String skillCode;

  /**
   * 构造 Skill 执行异常。
   *
   * @param skillCode Skill 编码
   * @param message   异常消息
   */
  public SkillExecutionException(String skillCode, String message) {
    super("SKILL_EXECUTION_ERROR", message);
    this.skillCode = skillCode;
  }

  /**
   * 构造 Skill 执行异常（含根因）。
   *
   * @param skillCode Skill 编码
   * @param message   异常消息
   * @param cause     根因
   */
  public SkillExecutionException(String skillCode, String message, Throwable cause) {
    super("SKILL_EXECUTION_ERROR", message, cause);
    this.skillCode = skillCode;
  }

  public String getSkillCode() {
    return skillCode;
  }
}
