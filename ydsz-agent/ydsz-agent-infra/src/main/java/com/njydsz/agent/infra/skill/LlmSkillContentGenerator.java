package com.njydsz.agent.infra.skill;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.skill.SkillContentGenerator;
import com.njydsz.common.json.YdszJson;

/**
 * 基于 LLM 的 Skill 内容生成器实现
 *
 * <p>调用 LLM 将自然语言描述转换为结构化的 SKILL.md 草稿。 生成的内容遵循以下规范（与 Snail AI 技能包格式兼容）：
 *
 * <ul>
 *   <li>顶部 YAML frontmatter 声明 name 和 description</li>
 *   <li>结构化流程章节（## 执行流程）</li>
 *   <li>输入参数说明</li>
 *   <li>输出格式定义</li>
 *   <li>错误处理策略</li>
 * </ul>
 *
 * <p>当 LLM 不可用时，退回基于规则的模板生成（最小可用草稿）。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class LlmSkillContentGenerator implements SkillContentGenerator {

  private final LlmClient llmClient;
  private final String model;

  /** 生成 SKILL.md 的系统提示词 */
  private static final String GENERATE_SYSTEM_PROMPT =
      """
          你是一个专业的 Skill 文档编写专家。你的任务是根据用户提供的名称和用途描述，生成一个标准的 SKILL.md 文件内容。

          SKILL.md 格式规范：
          1. 顶部使用 YAML frontmatter 声明 name 和 description
          2. "## 执行流程" 章节：按步骤描述 Skill 的执行逻辑（有序列表）
          3. "## 输入参数" 章节：描述 Skill 接收的参数
          4. "## 输出格式" 章节：描述 Skill 返回的数据结构
          5. "## 错误处理" 章节：描述异常情况的处理策略
          6. "## 使用示例" 章节：给出一个典型使用示例

          只输出 Markdown 内容，不要包含代码块标记以外的文字。
          """;

  /** 优化 SKILL.md 的系统提示词 */
  private static final String OPTIMIZE_SYSTEM_PROMPT =
      """
          你是一个专业的 Skill 文档优化专家。你的任务是优化给定的 SKILL.md 文件内容。

          优化目标：
          1. 使流程描述更清晰、更具可操作性
          2. 补充可能遗漏的边界条件和错误处理
          3. 确保输出格式定义完整
          4. 使整体结构更符合 SKILL.md 规范

          只输出优化后的完整 Markdown 内容，不要输出其他文字。
          """;

  /**
   * 构造 LLM Skill 内容生成器。
   *
   * @param llmClient LLM 客户端
   * @param model 使用的模型名称
   */
  public LlmSkillContentGenerator(LlmClient llmClient, String model) {
    this.llmClient = llmClient;
    this.model = model;
  }

  @Override
  public String generate(String name, String description) {
    String userPrompt =
        String.format(
            "请为以下 Skill 生成标准的 SKILL.md 文件内容：\n\n名称：%s\n\n描述：%s",
            name, description);
    try {
      String content =
          callLlm(
              GENERATE_SYSTEM_PROMPT,
              userPrompt,
              "skill-generate-" + name);
      // 确保输出以 frontmatter 开头
      if (!content.trim().startsWith("---")) {
        content = buildFallbackContent(name, description);
      }
      return content;
    } catch (Exception e) {
      log.warn("[SkillGen] LLM 生成失败，使用规则模板: name={}, err={}", name, e.getMessage());
      return buildFallbackContent(name, description);
    }
  }

  @Override
  public String optimize(String currentContent, String optimizationHint) {
    String userPrompt =
        String.format(
            "请对以下 SKILL.md 内容进行优化。\n\n优化方向：%s\n\n当前内容：\n%s",
            optimizationHint, currentContent);
    try {
      return callLlm(OPTIMIZE_SYSTEM_PROMPT, userPrompt, "skill-optimize");
    } catch (Exception e) {
      log.warn("[SkillGen] LLM 优化失败，返回原文: err={}", e.getMessage());
      return currentContent;
    }
  }

  private String callLlm(String systemPrompt, String userPrompt, String contextId) {
    List<ChatMessage> messages = List.of(
        ChatMessage.system(systemPrompt),
        ChatMessage.user(userPrompt, contextId));
    ChatRequest request = ChatRequest.builder()
        .model(model)
        .messages(messages)
        .temperature(0.5)
        .maxTokens(2048)
        .build();
    String response = llmClient.chat(request).getMessage().getContent();
    // 清理可能的 markdown 代码块包裹
    String content = response != null ? response.trim() : "";
    if (content.startsWith("```markdown")) {
      content = content.substring("```markdown".length()).trim();
    }
    if (content.startsWith("```")) {
      content = content.substring(3).trim();
    }
    if (content.endsWith("```")) {
      content = content.substring(0, content.length() - 3).trim();
    }
    return content;
  }

  /**
   * 基于规则生成模板内容（LLM 不可用时的降级方案）。
   *
   * @param name Skill 名称
   * @param description 描述
   * @return 模板化 SKILL.md 内容
   */
  private String buildFallbackContent(String name, String description) {
    return String.format(
        """
            ---
            name: %s
            description: %s
            ---

            # %s

            ## 执行流程

            1. 接收输入参数并校验
            2. 执行核心逻辑
            3. 处理可能的异常
            4. 返回结构化结果

            ## 输入参数

            根据实际业务需求定义。

            ## 输出格式

            建议返回 JSON 格式的结构化数据。

            ## 错误处理

            1. 参数校验不通过时返回明确的错误信息
            2. 业务异常时记录日志并返回友好的错误提示
            3. 网络异常时支持有限次数的重试

            ## 使用示例

            请在此处给出一个典型的调用示例。
            """,
        name, description, name);
  }
}
