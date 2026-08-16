package com.njydsz.agent.domain.model;

/**
 * 消息角色枚举（对标 OpenAI Chat Completions message.role）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum MessageRole {

  /** 系统指令（设定 Agent 人设/行为约束） */
  SYSTEM("system"),

  /** 用户消息 */
  USER("user"),

  /** 助手回复 */
  ASSISTANT("assistant"),

  /** 工具调用结果 */
  TOOL("tool");

  private final String apiValue;

  MessageRole(String apiValue) {
    this.apiValue = apiValue;
  }

  public String getApiValue() {
    return apiValue;
  }

  /**
   * 从 API 字符串解析角色枚举。
   *
   * <p>大小写不敏感匹配（如 {@code "System"}、{@code "SYSTEM"} 均解析为 {@link #SYSTEM}）；无法识别的字符串兜底返回 {@link
   * #USER}， 避免非法/新增角色值导致整条解析链路失败。
   *
   * @param value API 返回的角色字符串；{@code null} 时同样兜底为 USER
   * @return 对应的角色枚举，未知值返回 {@link #USER}
   */
  public static MessageRole fromApiValue(String value) {
    for (MessageRole role : values()) {
      if (role.apiValue.equalsIgnoreCase(value)) {
        return role;
      }
    }
    // 无法识别的 role 字符串兜底为 USER，避免非法/新增角色导致整条链路解析失败
    return USER;
  }
}
