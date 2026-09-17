package com.njydsz.agent.domain.skill;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Skill 执行上下文（值对象）
 *
 * <p>封装一次 Skill 调用所需的运行时参数，包括调用者信息、输入参数、
 * 超时配置和执行环境变量。
 *
 * @param skillCode    Skill 编码
 * @param tenantCode   租户编码（多租户隔离）
 * @param userId       触发用户 ID
 * @param inputParams  输入参数（key→value）
 * @param timeoutMs    执行超时（毫秒），0 表示使用 Skill 默认值
 * @param envVariables 环境变量注入
 * @param traceId      链路追踪 ID
 * @author ydsz-team
 * @since 26.09.17
 */
public record SkillExecutionContext(
    String skillCode,
    String tenantCode,
    String userId,
    Map<String, Object> inputParams,
    long timeoutMs,
    Map<String, String> envVariables,
    String traceId
) implements Serializable {

  private static final long serialVersionUID = 1L;

  public SkillExecutionContext {
    Objects.requireNonNull(skillCode, "skillCode 不能为 null");
    inputParams = inputParams != null ? Map.copyOf(inputParams) : Map.of();
    envVariables = envVariables != null ? Map.copyOf(envVariables) : Map.of();
    timeoutMs = Math.max(timeoutMs, 0);
  }

  /**
   * 获取输入参数值。
   *
   * @param key 参数键
   * @return 参数值（不存在返回 null）
   */
  public Object getInput(String key) {
    return inputParams.get(key);
  }

  /**
   * 获取输入参数字符串值。
   *
   * @param key 参数键
   * @return 字符串值（不存在返回空串）
   */
  public String getInputString(String key) {
    Object val = inputParams.get(key);
    return val != null ? val.toString() : "";
  }

  /**
   * 获取环境变量值。
   *
   * @param key 环境变量键
   * @return 值，不存在返回 null
   */
  public String getEnv(String key) {
    return envVariables.get(key);
  }

  /**
   * 创建 Builder。
   *
   * @return Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * SkillExecutionContext 构建器。
   */
  public static class Builder {
    private String skillCode;
    private String tenantCode;
    private String userId;
    private Map<String, Object> inputParams = Collections.emptyMap();
    private long timeoutMs;
    private Map<String, String> envVariables = Collections.emptyMap();
    private String traceId;

    public Builder skillCode(String skillCode) {
      this.skillCode = skillCode;
      return this;
    }

    public Builder tenantCode(String tenantCode) {
      this.tenantCode = tenantCode;
      return this;
    }

    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder inputParams(Map<String, Object> inputParams) {
      this.inputParams = inputParams;
      return this;
    }

    public Builder timeoutMs(long timeoutMs) {
      this.timeoutMs = timeoutMs;
      return this;
    }

    public Builder envVariables(Map<String, String> envVariables) {
      this.envVariables = envVariables;
      return this;
    }

    public Builder traceId(String traceId) {
      this.traceId = traceId;
      return this;
    }

    public SkillExecutionContext build() {
      return new SkillExecutionContext(
          skillCode, tenantCode, userId, inputParams, timeoutMs, envVariables, traceId);
    }
  }
}
