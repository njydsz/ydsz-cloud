package com.njydsz.pmis.userinfo.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.userinfo.api.fallback.BenchResourceClientFallback;

import com.njydsz.pmis.common.core.response.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 资源/Bench 数据 Feign 客户端（执行模块专用）
 *
 * <p>P1-12 跨模块真实聚合：
 * <ul>
 *   <li>{@link #getBenchDashboard()}：调用 user 服务获取 Bench 仪表盘</li>
 *   <li>{@link #listResourceAssignmentsByInitiation(String)}：调用 user 服务按项目查询资源分配</li>
 * </ul>
 *
 * <p>P2-1-followup: 从 project.feign 迁移至 common.feign，使用 {@link FeignClientConstants#USERINFO} 常量。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(
        name = FeignClientConstants.USERINFO,
        contextId = "benchResourceClient",
        fallbackFactory = BenchResourceClientFallback.class)
public interface BenchResourceClient {

    /**
     * Bench 仪表盘汇总（活跃池分布 + 累计闲置成本）
     *
     * @return Bench 仪表盘汇总数据
     */
    @GetMapping("/bench/dashboard")
    BaseResponse<Map<String, Object>> getBenchDashboard();

    /**
     * 按项目查询资源分配（甘特图数据源）
     *
     * @param initiationId 立项 ID
     * @return 资源分配列表（每条记录为一个 Map）
     */
    @GetMapping("/resourceAssignments/byInitiation/{initiationId}")
    BaseResponse<List<Map<String, Object>>> listResourceAssignmentsByInitiation(
            @PathVariable("initiationId") String initiationId);
}
