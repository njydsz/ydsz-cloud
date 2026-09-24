package com.njydsz.agent.infra.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.skill.SkillDescriptor;
import com.njydsz.agent.domain.skill.SkillExecutionContext;
import com.njydsz.agent.domain.skill.SkillExecutionException;
import com.njydsz.agent.domain.skill.SkillExecutionResult;
import com.njydsz.agent.domain.skill.SkillRegistry;
import com.njydsz.agent.domain.skill.SkillRuntime;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.agent.domain.tool.ToolRegistration;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.infra.tool.DefaultToolRegistry;

/**
 * Skill → Tool 适配器，桥接已有 {@link ToolRegistry}。
 *
 * <p>将所有已注册的 Skill 转换为 {@link ToolDefinition} 提供给 LLM；
 * 执行时解析 ToolCall 参数，构建 {@link SkillExecutionContext}，
 * 委托 {@link SkillRuntime} 执行并将 stdout 作为 JSON 字符串返回给 Tool 调用链。
 *
 * <p>典型调用路径：
 *
 * <pre>
 * LLM 选择 tool_call(skillCode, args)
 *   → ToolRegistry.execute(ToolCall)
 *   → SkillToolAdapter.execute(args)
 *   → SkillRuntime.execute(descriptor, context)
 *   → 返回 stdout 作为 tool 执行结果
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Component
public class SkillToolAdapter {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 8;

  /** 系统默认租户（未指定时使用） */
  private static final String DEFAULT_TENANT = "default";

  /** 系统默认用户（未指定时使用） */
  private static final String DEFAULT_USER = "system";

  /** Skill 注册中心 */
  private final SkillRegistry skillRegistry;

  /** Skill 运行时（组合路由器） */
  private final SkillRuntime skillRuntime;

  /**
   * 构造 Skill-Tool 适配器。
   *
   * @param skillRegistry Skill 注册中心
   * @param skillRuntime  Skill 运行时（应注入组合路由 CompositeSkillRuntime）
   */
  public SkillToolAdapter(SkillRegistry skillRegistry, SkillRuntime skillRuntime) {
    this.skillRegistry = skillRegistry;
    this.skillRuntime = skillRuntime;
  }

  /**
   * 根据已注册 Skill 列表，获取所有 Skill 的 Tool 定义。
   *
   * <p>供外部在启动时批量注册到 {@link ToolRegistry}。
   *
   * @return Skill 对应 Tool 定义列表
   */
  public List<ToolDefinition> getAllSkillToolDefinitions() {
    List<ToolDefinition> definitions = new ArrayList<>();
    for (SkillDescriptor descriptor : skillRegistry.findAll()) {
      definitions.add(toToolDefinition(descriptor));
    }
    return definitions;
  }

  /**
   * 将单个 Skill 注册到工具注册表。
   *
   * @param toolRegistry 目标工具注册表
   * @param descriptor   Skill 定义
   */
  public void registerSkillAsTool(ToolRegistry toolRegistry, SkillDescriptor descriptor) {
    ToolDefinition definition = toToolDefinition(descriptor);
    ToolExecutor executor = buildToolExecutor(descriptor);
    ToolRegistration registration = new ToolRegistration(definition, executor);

    // 使用 ToolRegistration 注册（保留 description 和 schema）
    if (toolRegistry instanceof DefaultToolRegistry defaultRegistry) {
      defaultRegistry.register(registration);
    } else {
      toolRegistry.register(descriptor.skillCode(), executor);
    }
    log.info("[SkillToolAdapter] 已注册 Skill 为 Tool: {}", descriptor.skillCode());
  }

  /**
   * 批量将注册中心中所有 Skill 注册到工具注册表。
   *
   * @param toolRegistry 目标工具注册表
   */
  public void registerAllSkillsAsTools(ToolRegistry toolRegistry) {
    for (SkillDescriptor descriptor : skillRegistry.findAll()) {
      registerSkillAsTool(toolRegistry, descriptor);
    }
  }

  // ==================== 私有转换与执行方法 ====================

  /**
   * 将 SkillDescriptor 转换为 ToolDefinition。
   *
   * @param descriptor Skill 定义
   * @return Tool 定义
   */
  private ToolDefinition toToolDefinition(SkillDescriptor descriptor) {
    String name = descriptor.skillCode();
    String description = descriptor.description();
    if (description == null || description.isBlank()) {
      description = descriptor.name();
    }
    Map<String, Object> paramsSchema = descriptor.inputSchema();
    if (paramsSchema == null || paramsSchema.isEmpty()) {
      paramsSchema = buildDefaultSchema();
    }
    return new ToolDefinition(name, description, paramsSchema);
  }

  /**
   * 构建 Skill 对应的 ToolExecutor。
   *
   * <p>执行时从 SkillRegistry 查找最新 Descriptor，组装 SkillExecutionContext 并委托
   * SkillRuntime 执行，结果以 JSON 字符串返回。
   *
   * @param descriptor Skill 定义
   * @return ToolExecutor 实现
   */
  private ToolExecutor buildToolExecutor(SkillDescriptor descriptor) {
    String skillCode = descriptor.skillCode();
    return arguments -> {
      SkillDescriptor latest = skillRegistry.findByCode(skillCode);
      if (latest == null) {
        log.warn("[SkillToolAdapter] Skill 未注册: {}", skillCode);
        return "{\"error\":\"Skill 未注册: " + skillCode + "\"}";
      }

      // 构建执行上下文
      SkillExecutionContext context = buildExecutionContext(skillCode, arguments);

      try {
        // 验证执行上下文
        skillRegistry.validateContext(context);

        // 委托运行时执行
        SkillExecutionResult result = skillRuntime.execute(latest, context);

        if (result.isSuccess()) {
          return result.stdout().isBlank() ? "{}" : result.stdout();
        } else {
          return "{\"error\":\"" + escapeJson(result.errorMessage()) + "\"}";
        }
      } catch (SkillExecutionException e) {
        log.error("[SkillToolAdapter] Skill 执行失败: {} - {}", skillCode, e.getMessage());
        return "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}";
      } catch (Exception e) {
        log.error("[SkillToolAdapter] Skill 执行异常: {} - {}", skillCode, e.getMessage(), e);
        return "{\"error\":\"执行异常: " + escapeJson(e.getMessage()) + "\"}";
      }
    };
  }

  /**
   * 从 Tool 调用参数构建 SkillExecutionContext。
   *
   * @param skillCode Skill 编码
   * @param arguments 输入参数
   * @return 执行上下文
   */
  private SkillExecutionContext buildExecutionContext(
      String skillCode, Map<String, Object> arguments) {
    Map<String, Object> inputParams = arguments != null ? arguments : Map.of();
    return SkillExecutionContext.builder()
        .skillCode(skillCode)
        .tenantCode(DEFAULT_TENANT)
        .userId(DEFAULT_USER)
        .inputParams(inputParams)
        .timeoutMs(0)
        .envVariables(Map.of())
        .traceId("")
        .build();
  }

  /**
   * 构建默认的参数 Schema（无参数时）。
   *
   * @return 空 Schema
   */
  private Map<String, Object> buildDefaultSchema() {
    Map<String, Object> schema = new HashMap<>(COLLECTION_CAPACITY);
    schema.put("type", "object");
    schema.put("properties", Map.of());
    return schema;
  }

  /**
   * 简易 JSON 字符串转义。
   *
   * @param value 原始字符串
   * @return 转义后字符串
   */
  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }
}
