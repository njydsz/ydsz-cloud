package com.njydsz.pmis.project.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.project.api.fallback.ProjectServiceClientFallback;

import com.njydsz.pmis.common.core.response.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 项目执行模块 Feign 客户端（供 Agent 工具调用真实数据源）
 *
 * <p>聚合工时异常统计、风险分页查询、EVM 仪表盘三个接口，供
 * {@code com.njydsz.pmis.agent.server.tool.TimesheetStatTool}、
 * {@code com.njydsz.pmis.agent.server.tool.RiskEventQueryTool}、
 * {@code com.njydsz.pmis.agent.server.tool.ProjectStatusTool}
 * 在 {@code pmis.agent.tool.mock-enabled=false} 时调用。
 *
 * <p>project 服务不可用时由 {@link ProjectServiceClientFallback} 返回降级空数据，
 * 避免 Agent 推理链路级联失败。
 *
 * <p>P2-1-followup: 从 agent.feign 迁移至 common.feign，使用 {@link FeignClientConstants#PROJECT} 常量。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P0-5)
 */
@FeignClient(
        name = FeignClientConstants.PROJECT,
        contextId = "projectServiceClient",
        fallbackFactory = ProjectServiceClientFallback.class
)
public interface ProjectServiceClient {

    /**
     * 工时异常统计（按项目 + 月份）
     *
     * @param initiationId 项目立项 ID
     * @param month        月份（yyyy-MM），为空时服务端取当前月
     * @return 异常统计 Map（overtimeCount/missingCount/abnormalCount/totalHours）
     */
    @GetMapping("/execution/timeEntry/abnormalStat")
    BaseResponse<Map<String, Object>> timeEntryAbnormalStat(
            @RequestParam("initiationId") String initiationId,
            @RequestParam(value = "month", required = false) String month);

    /**
     * 风险分页查询
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项 ID
     * @param riskLevel    风险等级过滤（HIGH/MEDIUM/LOW）
     * @return 分页结果 Map（含 records 列表与 total 总数）
     */
    @GetMapping("/execution/risk/page")
    BaseResponse<Map<String, Object>> riskPage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "100") int size,
            @RequestParam(value = "initiationId", required = false) String initiationId,
            @RequestParam(value = "riskLevel", required = false) String riskLevel);

    /**
     * EVM 挣值管理仪表盘
     *
     * @param initiationId 项目立项 ID
     * @return 仪表盘 Map（含 latestCpi/latestSpi/latestVac/measureCount 等）
     */
    @GetMapping("/execution/evm/dashboard")
    BaseResponse<Map<String, Object>> evmDashboard(@RequestParam("initiationId") String initiationId);
}
