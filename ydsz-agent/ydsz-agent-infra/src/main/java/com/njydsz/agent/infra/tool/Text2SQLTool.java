package com.njydsz.agent.infra.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.gateway.Text2SQLService;
import com.njydsz.agent.domain.tool.ToolExecutor;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Text2SQL 工具实现（注册到 ToolRegistry）。
 *
 * <p>允许 Agent 通过自然语言查询数据库，自动转换为 SQL 并执行返回结果。 安全护栏（仅 SELECT、注入检测、行数限制）由 {@link Text2SQLService} 实现层保证。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Text2SQLTool implements ToolExecutor {

  /** 参数名：自然语言查询 */
  private static final String PARAM_QUERY = "query";

  private final Text2SQLService text2SQLService;

  @Override
  public String execute(Map<String, Object> arguments) throws Exception {
    if (arguments == null || !arguments.containsKey(PARAM_QUERY)) {
      return YdszJson.toJson(Map.of("error", "缺少必需参数: query"));
    }
    String query = String.valueOf(arguments.get(PARAM_QUERY));
    if (query.isBlank()) {
      return YdszJson.toJson(Map.of("error", "参数 query 不能为空"));
    }
    String tenantId = resolveTenantId();
    LOG.info("[Text2SQLTool] 执行自然语言查询: tenant={}, query={}", tenantId, query);
    try {
      Text2SQLService.Text2SQLResult result = text2SQLService.query(query, tenantId);
      return YdszJson.toJson(
          Map.of(
              "columns", result.columns(),
              "rows", result.rows(),
              "rowCount", result.rowCount(),
              "sql", result.generatedSql(),
              "executionTimeMs", result.executionTimeMs()));
    } catch (Text2SQLService.Text2SQLException e) {
      LOG.warn("[Text2SQLTool] Text2SQL 执行失败: errorCode={}, msg={}", e.getErrorCode(), e.getMessage());
      return YdszJson.toJson(Map.of("error", e.getMessage(), "errorCode", e.getErrorCode()));
    } catch (Exception e) {
      LOG.error("[Text2SQLTool] Text2SQL 未知异常: {}", e.getMessage(), e);
      return YdszJson.toJson(Map.of("error", "Text2SQL 执行异常: " + e.getMessage()));
    }
  }

  private String resolveTenantId() {
    try {
      String tenantId = TenantContextHolder.getTenantId();
      return tenantId != null && !tenantId.isBlank() ? tenantId : "default";
    } catch (Exception e) {
      LOG.debug("[Text2SQLTool] 获取租户 ID 失败，使用默认值");
      return "default";
    }
  }

  /**
   * 获取 Text2SQL 工具的 OpenAI function calling 定义。
   *
   * <p>供 ToolRegistry 组装 tools 数组使用。
   *
   * @return ToolDefinition 列表
   */
  public static List<com.njydsz.agent.domain.model.ToolDefinition> toolDefinitions() {
    Map<String, Object> parametersSchema =
        Map.of(
            "type", "object",
            "properties",
                Map.of(
                    "query",
                    Map.of(
                        "type", "string",
                        "description", "自然语言查询（如：查询最近 7 天创建的项目数量、列出所有状态为进行中的任务）")),
            "required", List.of("query"));
    return List.of(
        new com.njydsz.agent.domain.model.ToolDefinition(
            "query_database",
            "通过自然语言查询数据库，自动转换为 SQL 执行并返回结果。支持聚合查询、条件过滤、排序等常见查询操作。",
            parametersSchema));
  }
}
