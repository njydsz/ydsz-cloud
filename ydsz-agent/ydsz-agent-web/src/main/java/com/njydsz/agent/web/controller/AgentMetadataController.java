package com.njydsz.agent.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.infra.llm.LlmClientRouter;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;

/**
 * Agent 元数据查询 Controller（可用模型 / 已注册工具）
 *
 * <p>从原 {@link AgentController} 拆分而来，仅承载只读的元数据查询接口：
 *
 * <ul>
 *   <li>{@code GET /api/v1/agent/models} - 获取可用模型 / Provider 列表
 *   <li>{@code GET /api/v1/agent/tools} - 获取已注册工具列表
 * </ul>
 *
 * <p>这些接口供前端 Agent 编辑器渲染「可用模型」下拉选择器和「可用工具」勾选列表， 不涉及 Agent 执行逻辑。Agent 的同步 / 流式执行见 {@link
 * AgentController}。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>多 LLM Provider 元数据暴露（通过 {@link LlmClientRouter} 统一抽象）
 *   <li>工具注册中心元数据暴露（通过 {@link ToolRegistry} 查询）
 * </ul>
 *
 * <h3>设计原则</h3>
 *
 * <p>本 Controller 仅做参数透传与 VO 转换，所有元数据来源由 {@link LlmClient} 与 {@link ToolRegistry} 提供，不包含业务编排逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AgentController Agent 执行接口（同步 / SSE 流式）
 * @see LlmClient LLM 客户端抽象
 * @see LlmClientRouter 多 Provider 路由器
 * @see ToolRegistry 工具注册中心
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "Agent 元数据查询", description = "可用模型 / 已注册工具元数据查询")
public class AgentMetadataController {

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 工具注册中心（查询已注册工具元数据） */
  private final ToolRegistry toolRegistry;

  /**
   * 获取可用模型 / Provider 列表。
   *
   * <p>当注入的 {@link LlmClient} 是 {@link LlmClientRouter} 时，返回其注册的所有可用 Provider； 否则返回单一 Provider。
   *
   * @return 统一响应结果，data 为 {@code [{provider, available}, ...]} 格式的列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_METADATA_VIEW)
  @GetMapping("/models")
  @Operation(summary = "获取可用模型列表", description = "返回当前 Agent 支持的 LLM Provider 列表")
  public YdszResponse<List<Map<String, Object>>> models() {
    List<Map<String, Object>> result = new ArrayList<>(16);
    if (llmClient instanceof LlmClientRouter router) {
      for (String provider : router.getAvailableProviders()) {
        result.add(Map.of("provider", provider, "available", true));
      }
    } else {
      result.add(Map.of("provider", llmClient.getProvider(), "available", true));
    }
    return YdszResponse.success(result);
  }

  /**
   * 获取已注册工具列表。
   *
   * <p>从 {@link ToolRegistry} 查询所有已注册工具的元数据（名称 + 描述），供前端 Agent 编辑器
   * 渲染「可用工具」下拉选择器。注意：本接口仅返回工具元数据，工具的实际调用由 Agent 内部完成。
   *
   * @return 统一响应结果，data 为 {@code [{name, description}, ...]} 格式的列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_METADATA_VIEW)
  @GetMapping("/tools")
  @Operation(summary = "获取已注册工具列表", description = "返回工具注册中心中所有已注册工具的元数据")
  public YdszResponse<List<Map<String, Object>>> tools() {
    var defs = toolRegistry.getToolDefinitions();
    List<Map<String, Object>> result =
        defs.stream()
            .map(
                td ->
                    Map.<String, Object>of(
                        "name",
                        td.getName(),
                        "description",
                        td.getDescription() != null ? td.getDescription() : ""))
            .toList();
    return YdszResponse.success(result);
  }
}
