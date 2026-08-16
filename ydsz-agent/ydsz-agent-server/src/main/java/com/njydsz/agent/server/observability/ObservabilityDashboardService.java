package com.njydsz.agent.server.observability;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;

/**
 * 可观测性面板服务
 *
 * <p>聚合 Agent 模块的运行指标，为前端面板提供统一数据源。
 * 数据来源：
 * <ul>
 *   <li>{@link AgentRuntimeMetrics} — 会话活跃度、执行耗时、TTFT</li>
 *   <li>{@link CostAnalysisService} — Token 成本统计</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class ObservabilityDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityDashboardService.class);

    private final AgentRuntimeMetrics runtimeMetrics;
    private final CostAnalysisService costAnalysisService;

    public ObservabilityDashboardService(AgentRuntimeMetrics runtimeMetrics,
                                          CostAnalysisService costAnalysisService) {
        this.runtimeMetrics = runtimeMetrics;
        this.costAnalysisService = costAnalysisService;
    }

    /**
     * 获取面板概览数据（总览卡片 + 近期趋势）。
     *
     * @return 面板数据 DTO
     */
    public DashboardOverviewDTO getOverview() {
        log.info("[Observability] 查询面板概览数据");

        // 今日成本统计
        LocalDate today = LocalDate.now();
        Map<String, CostAnalysisService.ModelCostStats> todayCostByModel =
                costAnalysisService.getStatsByModel(
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay());

        double totalCostUsd = todayCostByModel.values().stream()
                .mapToDouble(CostAnalysisService.ModelCostStats::totalCostUsd)
                .sum();
        long totalTokens = todayCostByModel.values().stream()
                .mapToLong(CostAnalysisService.ModelCostStats::totalTokens)
                .sum();

        // 读取当前活跃会话数（Gauge 值）
        int activeConvs = (int) runtimeMetrics.getActiveConversations();

        return new DashboardOverviewDTO(
                LocalDateTime.now(),
                totalCostUsd,
                totalTokens,
                todayCostByModel,
                activeConvs,
                0L);
    }

    /**
     * 获取模型使用分布统计。
     *
     * @param days 统计天数（从今天往前推）
     * @return 各模型的用量分布
     */
    public List<ModelUsageDTO> getModelUsageDistribution(int days) {
        log.info("[Observability] 查询模型分布: last {} days", days);
        LocalDate endDate = LocalDate.now().plusDays(1);
        LocalDate startDate = endDate.minusDays(days);

        Map<String, CostAnalysisService.ModelCostStats> statsMap =
                costAnalysisService.getStatsByModel(
                        startDate.atStartOfDay(),
                        endDate.atStartOfDay());

        return statsMap.entrySet().stream()
                .map(entry -> new ModelUsageDTO(
                        entry.getKey(),
                        entry.getValue().totalTokens(),
                        entry.getValue().totalCostUsd(),
                        entry.getValue().callCount()))
                .toList();
    }

    /**
     * 面板概览 DTO
     *
     * @param timestamp          数据快照时间
     * @param todayCostUsd       今日总成本（USD）
     * @param todayTotalTokens   今日总 Token 数
     * @param costByModel        按模型分组的成本统计
     * @param activeConversations 当前活跃会话数
     * @param totalMessages      总消息数
     */
    public record DashboardOverviewDTO(
            LocalDateTime timestamp,
            double todayCostUsd,
            long todayTotalTokens,
            Map<String, CostAnalysisService.ModelCostStats> costByModel,
            int activeConversations,
            long totalMessages) {
    }

    /**
     * 模型用量分布 DTO
     *
     * @param modelName 模型名称
     * @param tokens    总 Token 数
     * @param costUsd   总成本（USD）
     * @param callCount 调用次数
     */
    public record ModelUsageDTO(String modelName, long tokens, double costUsd, long callCount) {
    }
}
