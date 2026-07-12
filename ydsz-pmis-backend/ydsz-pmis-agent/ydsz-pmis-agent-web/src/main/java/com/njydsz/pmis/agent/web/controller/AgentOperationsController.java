package com.njydsz.pmis.agent.web.controller.agent;

import com.njydsz.pmis.agent.server.service.agent.AgentService;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Agent 运营数据看板 Controller（P2-9）
 *
 * <p>提供 AI 运营关键指标：Token 消耗趋势、成本按 Agent 分布、
 * 对话量统计、模型调用延迟分布。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/agent/operations")
@RequiredArgsConstructor
@Tag(name = "AI Agent 运营看板", description = "Token 成本、对话量、模型延迟等运营指标")
public class AgentOperationsController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Token 消耗与成本概览
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 概览数据（总 Token、总成本、日均成本、按模型分布）
     */
    @GetMapping("/cost/summary")
    @Operation(summary = "Token 成本概览")
    public BaseResponse<Map<String, Object>> costSummary(
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String tenantId = TenantContext.getTenantId();
            LocalDate end = endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
            LocalDate start = startDate.isEmpty() ? end.minusDays(30) : LocalDate.parse(startDate);

            // 总 Token 与成本
            Map<String, Object> totals = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(total_tokens), 0) AS total_tokens, " +
                            "COALESCE(SUM(total_cost), 0) AS total_cost, " +
                            "COALESCE(AVG(total_cost), 0) AS avg_cost, " +
                            "COUNT(*) AS call_count " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1",
                    tenantId, start, end
            );
            BaseResponse.put("totals", totals);

            // 按模型分组
            List<Map<String, Object>> byModel = jdbcTemplate.queryForList(
                    "SELECT model_name, COUNT(*) AS call_count, " +
                            "SUM(total_tokens) AS tokens, SUM(total_cost) AS cost, " +
                            "AVG(latency_ms) AS avg_latency " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1 " +
                            "GROUP BY model_name ORDER BY cost DESC",
                    tenantId, start, end
            );
            BaseResponse.put("byModel", byModel);

            // 按日期趋势
            List<Map<String, Object>> dailyTrend = jdbcTemplate.queryForList(
                    "SELECT DATE(created_at) AS date, " +
                            "SUM(total_tokens) AS tokens, SUM(total_cost) AS cost, " +
                            "COUNT(*) AS call_count " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1 " +
                            "GROUP BY DATE(created_at) ORDER BY date",
                    tenantId, start, end
            );
            BaseResponse.put("dailyTrend", dailyTrend);

        } catch (Exception e) {
            log.warn("[AgentOperations] 成本概览查询失败: {}", e.getMessage());
            BaseResponse.put("totals", Map.of("total_tokens", 0, "total_cost", 0, "avg_cost", 0, "call_count", 0));
            BaseResponse.put("byModel", List.of());
            BaseResponse.put("dailyTrend", List.of());
        }

        return BaseResponse.ok(result);
    }

    /**
     * 对话量统计
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 对话量数据（总对话数、日均、按 Agent 分布）
     */
    @GetMapping("/conversations/stats")
    @Operation(summary = "对话量统计")
    public BaseResponse<Map<String, Object>> conversationStats(
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String tenantId = TenantContext.getTenantId();
            LocalDate end = endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
            LocalDate start = startDate.isEmpty() ? end.minusDays(30) : LocalDate.parse(startDate);

            // 总对话数
            Map<String, Object> totals = jdbcTemplate.queryForMap(
                    "SELECT COUNT(DISTINCT session_id) AS total_sessions, " +
                            "COUNT(*) AS total_messages, " +
                            "COUNT(DISTINCT user_id) AS unique_users " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1",
                    tenantId, start, end
            );
            BaseResponse.put("totals", totals);

            // 按 Agent 分组
            List<Map<String, Object>> byAgent = jdbcTemplate.queryForList(
                    "SELECT agent_id, agent_name, " +
                            "COUNT(DISTINCT session_id) AS sessions, " +
                            "COUNT(*) AS messages, " +
                            "AVG(total_tokens) AS avg_tokens_per_msg " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1 " +
                            "GROUP BY agent_id, agent_name ORDER BY sessions DESC LIMIT 20",
                    tenantId, start, end
            );
            BaseResponse.put("byAgent", byAgent);

        } catch (Exception e) {
            log.warn("[AgentOperations] 对话量统计查询失败: {}", e.getMessage());
            BaseResponse.put("totals", Map.of("total_sessions", 0, "total_messages", 0, "unique_users", 0));
            BaseResponse.put("byAgent", List.of());
        }

        return BaseResponse.ok(result);
    }

    /**
     * 模型延迟分布
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 延迟分布（P50/P90/P99/AVG 按模型分组）
     */
    @GetMapping("/latency/stats")
    @Operation(summary = "模型延迟分布")
    public BaseResponse<List<Map<String, Object>>> latencyStats(
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate) {
        try {
            String tenantId = TenantContext.getTenantId();
            LocalDate end = endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
            LocalDate start = startDate.isEmpty() ? end.minusDays(30) : LocalDate.parse(startDate);

            List<Map<String, Object>> stats = jdbcTemplate.queryForList(
                    "SELECT model_name, " +
                            "AVG(latency_ms) AS avg_latency, " +
                            "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY latency_ms) AS p50, " +
                            "PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY latency_ms) AS p90, " +
                            "PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99, " +
                            "MAX(latency_ms) AS max_latency, " +
                            "COUNT(*) AS call_count " +
                            "FROM agent_trace " +
                            "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1 " +
                            "GROUP BY model_name ORDER BY avg_latency DESC",
                    tenantId, start, end
            );
            return BaseResponse.ok(stats);
        } catch (Exception e) {
            log.warn("[AgentOperations] 延迟统计查询失败: {}", e.getMessage());
            return BaseResponse.ok(new ArrayList<>());
        }
    }

    /**
     * 对话搜索
     *
     * @param keyword   搜索关键词
     * @param agentId   Agent ID（可选）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @param page      页码
     * @param size      每页条数
     * @return 对话记录列表
     */
    @GetMapping("/conversations/search")
    @Operation(summary = "对话搜索")
    public BaseResponse<Map<String, Object>> searchConversations(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String agentId,
            @RequestParam(defaultValue = "") String startDate,
            @RequestParam(defaultValue = "") String endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            String tenantId = TenantContext.getTenantId();
            LocalDate end = endDate.isEmpty() ? LocalDate.now() : LocalDate.parse(endDate);
            LocalDate start = startDate.isEmpty() ? end.minusDays(30) : LocalDate.parse(startDate);

            StringBuilder whereClause = new StringBuilder(
                    "WHERE tenant_id = ? AND created_at >= ? AND created_at < ? + 1");
            List<Object> params = new ArrayList<>(List.of(tenantId, start, end));

            if (keyword != null && !keyword.isEmpty()) {
                whereClause.append(" AND (user_message ILIKE ? OR assistant_message ILIKE ?)");
                String pattern = "%" + keyword + "%";
                params.add(pattern);
                params.add(pattern);
            }
            if (agentId != null && !agentId.isEmpty()) {
                whereClause.append(" AND agent_id = ?");
                params.add(agentId);
            }

            // 总数
            Long total = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM agent_trace " + whereClause,
                    Long.class, params.toArray()
            );
            BaseResponse.put("total", total != null ? total : 0);

            // 分页数据
            int offset = (page - 1) * size;
            params.add(size);
            params.add(offset);

            List<Map<String, Object>> records = jdbcTemplate.queryForList(
                    "SELECT id, session_id, agent_id, agent_name, model_name, " +
                            "user_message, assistant_message, total_tokens, total_cost, " +
                            "latency_ms, status, created_at " +
                            "FROM agent_trace " + whereClause +
                            " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    params.toArray()
            );
            BaseResponse.put("records", records);
            BaseResponse.put("page", page);
            BaseResponse.put("size", size);

        } catch (Exception e) {
            log.warn("[AgentOperations] 对话搜索查询失败: {}", e.getMessage());
            BaseResponse.put("total", 0);
            BaseResponse.put("records", new ArrayList<>());
            BaseResponse.put("page", page);
            BaseResponse.put("size", size);
        }

        return BaseResponse.ok(result);
    }
}
