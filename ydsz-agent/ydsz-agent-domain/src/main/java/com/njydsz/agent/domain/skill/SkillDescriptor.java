package com.njydsz.agent.domain.skill;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Skill 定义描述（值对象）
 *
 * <p>声明一个可复用、可版本化的能力单元。对应 agents-flex 的 Skill Package 概念。
 *
 * <h3>结构</h3>
 *
 * <ul>
 *   <li>{@code skillMd} — SKILL.md 说明与流程</li>
 *   <li>{@code scripts} — 可执行脚本路径列表</li>
 *   <li>{@code assets} — 模板与资源路径列表</li>
 * </ul>
 *
 * <h3>数据来源</h3>
 *
 * <ul>
 *   <li>从本地目录或远程存储发现并加载</li>
 *   <li>通过 {@code @Skill} 注解在代码中声明（Code Skill）</li>
 * </ul>
 *
 * @param skillCode   Skill 全局唯一编码
 * @param name        可读名称
 * @param description 用途描述（供 LLM 理解）
 * @param version     语义版本号
 * @param skillMd     SKILL.md 内容
 * @param scripts     脚本路径列表
 * @param assets      资源路径列表
 * @param inputSchema 入参 JSON Schema（Code Skill 使用，File Skill 为空）
 * @param metadata    扩展元数据（作者、标签、依赖等）
 * @param targetType  默认执行目标（local / sandbox）
 * @author ydsz-team
 * @since 26.09.17
 */
public record SkillDescriptor(
    String skillCode,
    String name,
    String description,
    String version,
    String skillMd,
    List<String> scripts,
    List<String> assets,
    Map<String, Object> inputSchema,
    Map<String, String> metadata,
    SkillExecutionTarget targetType
) implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 默认版本号 */
  private static final String DEFAULT_VERSION = "1.0.0";

  public SkillDescriptor {
    Objects.requireNonNull(skillCode, "skillCode 不能为 null");
    Objects.requireNonNull(name, "name 不能为 null");
    scripts = scripts != null ? List.copyOf(scripts) : List.of();
    assets = assets != null ? List.copyOf(assets) : List.of();
    inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
    metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    version = (version != null && !version.isBlank()) ? version : DEFAULT_VERSION;
    targetType = targetType != null ? targetType : SkillExecutionTarget.LOCAL;
  }

  /**
   * 获取指定元数据值。
   *
   * @param key 元数据键
   * @return 值，不存在返回空串
   */
  public String getMeta(String key) {
    return metadata.getOrDefault(key, "");
  }

  /**
   * 判断是否为需要沙箱执行的 Skill。
   *
   * @return true=需沙箱隔离执行
   */
  public boolean isSandboxRequired() {
    return targetType == SkillExecutionTarget.SANDBOX;
  }
}
