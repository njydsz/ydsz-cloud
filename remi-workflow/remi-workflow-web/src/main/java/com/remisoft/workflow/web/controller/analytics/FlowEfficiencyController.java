package com.remisoft.workflow.web.controller.analytics;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.remisoft.common.auth.context.AuthContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.workflow.server.service.FlowEfficiencyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批效率分析 Controller
 *
 * <p>业务背景：工作流审批效率分析是运营管理的核心能力，对标钉钉/飞书审批后台的
 * 「效率分析」面板。为运维人员提供审批单量统计、节点瓶颈排名、审批人效率排名、
 * 审批趋势分析、流程健康度综合评分等能力。
 *
 * <p>核心能力：
 * <ul>
 *   <li>审批效率统计 — 单量/平均耗时/代批率/超期率</li>
 *   <li>节点瓶颈排名 — 平均耗时 Top N 节点</li>
 *   <li>审批人效率排名 — 人均审批耗时排名</li>
 *   <li>审批趋势 — 按日/周/月聚合</li>
 *   <li>流程健康度评分 — 0-100 分综合评级</li>
 * </ul>
 *
 * <p>从原 {@code FlowMonitorController} 拆分而来，与 {@link FlowMonitorDashboardController}
 * 共享基路径 {@code /api/v1/workflow/engine}，所有端点 URL 保持不变。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see FlowEfficiencyService 效率分析服务
 * @see FlowMonitorDashboardController 监控看板 Controller
 */
@Slf4j
@RestController
@Tag(name = "workflow-efficiency", description = "工作流审批效率分析接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowEfficiencyController {

    /** GAP-P2: 审批效率分析服务 */
    private final FlowEfficiencyService efficiencyService;

    /**
     * GAP-P2: 审批效率统计 — 单量/平均耗时/代批率/超期率
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 统计结果
     */
    @GetMapping("/efficiency/stats")
    public BaseResponse<Map<String, Object>> efficiencyStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(efficiencyService.efficiencyStats(tenantId, startTime, endTime));
    }

    /**
     * GAP-P2: 节点瓶颈排名
     *
     * @param flowCode 流程编码（可选）
     * @param limit    返回条数上限
     * @return 瓶颈节点列表
     */
    @GetMapping("/efficiency/bottleneck")
    public BaseResponse<List<Map<String, Object>>> bottleneckRanking(
            @RequestParam(required = false) String flowCode,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(efficiencyService.bottleneckRanking(tenantId, flowCode, limit));
    }

    /**
     * GAP-P2: 审批人效率排名
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param limit     返回条数上限
     * @return 审批人排名列表
     */
    @GetMapping("/efficiency/approverRanking")
    public BaseResponse<List<Map<String, Object>>> approverRanking(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(efficiencyService.approverRanking(tenantId, startTime, endTime, limit));
    }

    /**
     * GAP-P2: 审批趋势
     *
     * @param interval  聚合粒度：DAY / WEEK / MONTH
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 趋势列表
     */
    @GetMapping("/efficiency/trend")
    public BaseResponse<List<Map<String, Object>>> approvalTrend(
            @RequestParam(defaultValue = "DAY") String interval,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(efficiencyService.approvalTrend(tenantId, interval, startTime, endTime));
    }

    /**
     * P1: 流程健康度综合评分
     *
     * <p>返回 0-100 分综合评分及 EXCELLENT/GOOD/FAIR/POOR 评级，含各维度扣分明细。
     *
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 评分结果：score / level / deductions / totalCount / anomalyCount / overdueRate / proxyRate / avgDurationMs
     */
    @Operation(summary = "流程健康度综合评分")
    @GetMapping("/efficiency/healthScore")
    public BaseResponse<Map<String, Object>> healthScore(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        String tenantId = AuthContext.getTenantIdOrDefault("1");
        return BaseResponse.success(efficiencyService.healthScore(tenantId, startTime, endTime));
    }
}
