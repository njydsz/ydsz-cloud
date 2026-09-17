package com.njydsz.agent.web.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.SemanticToolSearchService;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.PermissionCodes;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;

/**
 * Tool 查询与语义搜索 Controller
 *
 * <p>提供工具的语义搜索和全量列表接口：
 *
 * <ul>
 *   <li>{@code GET /api/agent/tool/search} - 根据自然语言查询语义匹配工具
 *   <li>{@code GET /api/agent/tool/list} - 全量列出已注册工具
 * </ul>
 *
 * <p>语义搜索通过 embedding 向量相似度匹配，精准响应用户意图；
 * 全量列出则供前端编辑器展示可选工具列表。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@ApiVersion("26.09.17")
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Tool 查询与搜索", description = "已注册工具的语义搜索 / 全量列表")
public class ToolController {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 默认 topK */
  private static final int DEFAULT_TOP_K = 5;

  /** 最大 topK */
  private static final int MAX_TOP_K = 20;

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /** 语义搜索服务 */
  private final SemanticToolSearchService semanticToolSearchService;

  /**
   * 语义搜索工具
   *
   * <p>通过 embedding 向量相似度匹配，返回与查询最相关的工具列表。
   * 当 LLM 不可用时自动降级为字符串包含匹配。
   *
   * @param query 自然语言查询（非空）
   * @param topK 返回数量上限（默认 5，最大 20）
   * @return 按相关度降序排列的工具列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_METADATA_VIEW)
  @GetMapping("/tool/search")
  @Operation(
      summary = "语义搜索工具",
      description = "通过 embedding 相似度匹配返回与查询最相关的工具列表，LLM 不可用时降级为字符串匹配")
  public YdszResponse<List<Map<String, Object>>> searchTools(
      @Parameter(description = "自然语言查询") @RequestParam String query,
      @Parameter(description = "返回数量上限（默认 5，最大 20）") @RequestParam(
            required = false,
            defaultValue = "5")
          Integer topK) {
    // 参数校验与边界控制
    if (query == null || query.isBlank()) {
      return YdszResponse.success(List.of());
    }
    int effectiveTopK = (topK == null || topK <= 0) ? DEFAULT_TOP_K : Math.min(topK, MAX_TOP_K);

    List<ToolDefinition> results = semanticToolSearchService.search(query, effectiveTopK);
    List<Map<String, Object>> items = toResponseList(results);
    return YdszResponse.success(items);
  }

  /**
   * 全量列出已注册工具
   *
   * @return 全部已注册工具的名称和描述列表
   */
  @AuthApiPermission(apiCodes = PermissionCodes.AGENT_METADATA_VIEW)
  @GetMapping("/tool/list")
  @Operation(summary = "全量列出已注册工具", description = "返回工具注册中心中所有已注册工具的名称和描述")
  public YdszResponse<List<Map<String, Object>>> listAllTools() {
    List<ToolDefinition> all = toolRegistry.getToolDefinitions();
    List<Map<String, Object>> items = toResponseList(all);
    return YdszResponse.success(items);
  }

  /**
   * 将工具定义列表转换为前端响应格式。
   *
   * @param tools 工具定义列表
   * @return 包含 name + description 的 Map 列表
   */
  private List<Map<String, Object>> toResponseList(List<ToolDefinition> tools) {
    List<Map<String, Object>> items = new ArrayList<>(tools.size());
    for (ToolDefinition td : tools) {
      Map<String, Object> map = new LinkedHashMap<>(COLLECTION_CAPACITY);
      map.put("name", td.getName());
      map.put("description", td.getDescription() != null ? td.getDescription() : "");
      items.add(map);
    }
    return items;
  }
}
