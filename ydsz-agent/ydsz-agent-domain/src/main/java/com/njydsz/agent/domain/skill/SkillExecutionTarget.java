package com.njydsz.agent.domain.skill;

/**
 * Skill 执行目标环境枚举
 *
 * <p>定义 Skill 运行时可路由到的执行环境。对应 agents-flex 的三种执行目标：
 *
 * <ul>
 *   <li>LOCAL — 本地进程（可信任务与本地调试）</li>
 *   <li>SANDBOX — Docker 容器隔离执行（生产环境推荐）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public enum SkillExecutionTarget {

  /** 本地进程执行（默认，可信任务与本地调试） */
  LOCAL("local", "本地进程执行"),

  /** Docker 容器隔离执行（不可信代码、生产推荐） */
  SANDBOX("sandbox", "Docker 沙箱隔离执行");

  private final String code;

  private final String description;

  SkillExecutionTarget(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 根据字符串编码解析枚举（忽略大小写）。
   *
   * @param code 编码字符串
   * @return 对应枚举，未匹配返回 LOCAL
   */
  public static SkillExecutionTarget fromCode(String code) {
    if (code == null || code.isBlank()) {
      return LOCAL;
    }
    for (SkillExecutionTarget target : values()) {
      if (target.code.equalsIgnoreCase(code)) {
        return target;
      }
    }
    return LOCAL;
  }
}
