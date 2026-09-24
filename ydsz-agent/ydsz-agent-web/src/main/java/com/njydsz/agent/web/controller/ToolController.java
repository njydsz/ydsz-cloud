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
   * <p>通过 Embedding 向量相似度匹配，返回与查询最相关的工具列表。
   * 处理流程：
   *
   * <ol>
   *   <li>对 {@code query} 调用 Embedding 模型（如 OpenAI text-embedding-3-small / 自部署模型）生成查询向量</li>
   *   <li>在预构建的工具向量索引中执行 ANN（近似最近邻）检索，候选集大小为 topK * 2</li>
   *   <li>对候选集按 cosine 相似度精排，取 TopK 返回</li>
   * </ol>
   *
   * <p>降级策略：当 Embedding 模型不可用或超时时，自动降级为字符串包含匹配（case-insensitive substring），
   * 标注 {@code degraded=true} 供前端提示用户。
   *
   * <p>Token 消耗：Embedding 调用消耗极少 Token（约 query 长度 / 4），
   * 可忽略不计。语义搜索本身不使用 LLM 生成能力。
   *
   * @param query 自然语言查询（非空，建议长度 5-200 字符），如"查询订单状态"、"发送邮件"
   * @param topK 返回数量上限（默认 5，有效范围 1-20），null 或 <= 0 时使用默认值 5
   * @return 统一响应结果，data 为 {@code [{name, description}]} 列表，按相似度降序排列；query 为空时返回空列表
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
   * <p>返回工具注册中心（{@link ToolRegistry}）中所有已注册工具的元数据列表。
   * 数据来源：应用启动时通过 SPI / 注解扫描加载的工具定义集合。
   *
   * <p>不消耗 LLM Token。返回结果通常供前端 Agent 编辑器展示"可选工具列表"全量数据。
   * 若仅需与当前查询相关的子集，建议使用语义搜索接口 {@link #searchTools}。
   *
   * @return 统一响应结果，data 为 {@code [{name, description}]} 列表；无注册工具时返回空列表
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
