package com.njydsz.agent.domain.skill;

/**
 * Skill 内容 AI 辅助生成接口（domain 层契约）
 *
 * <p>基于自然语言描述生成 SKILL.md 草稿内容，降低开发者创建 Skill 的门槛。 对应 Snail AI 技能管理模块的"AI 生成/优化"能力。生成流程：
 *
 * <ol>
 *   <li>用户提供 Skill 名称 + 功能描述</li>
 *   <li>LLM 根据描述生成结构化的 SKILL.md Markdown 内容</li>
 *   <li>开发者可在线编辑后保存</li>
 * </ol>
 *
 * <p>LLM 返回内容采用 YAML frontmatter 元数据格式，与 Snail AI 技能包规范一致。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
public interface SkillContentGenerator {

  /**
   * 根据自然语言描述生成 SKILL.md 草稿内容。
   *
   * @param name Skill 名称
   * @param description 功能描述（自然语言）
   * @return 生成的 SKILL.md 内容（含 YAML frontmatter）
   */
  String generate(String name, String description);

  /**
   * 基于当前内容进行 AI 优化。
   *
   * @param currentContent 当前 SKILL.md 内容
   * @param optimizationHint 优化方向（如"增加错误处理章节"、"结构更清晰"）
   * @return 优化后的 SKILL.md 内容
   */
  String optimize(String currentContent, String optimizationHint);
}
