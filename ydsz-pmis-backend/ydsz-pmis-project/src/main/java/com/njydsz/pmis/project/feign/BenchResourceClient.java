package com.njydsz.pmis.project.feign;

import com.njydsz.pmis.common.api.Result;
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
 *   <li>{@link #listResourceAssignmentsByInitiation(Long)}：调用 user 服务按项目查询资源分配</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-userinfo", fallbackFactory = BenchResourceClientFallback.class)
public interface BenchResourceClient {

    /**
     * Bench 仪表盘汇总（活跃池分布 + 累计闲置成本）
     *
     * @return Bench 仪表盘汇总数据
     */
    @GetMapping("/bench/dashboard")
    Result<Map<String, Object>> getBenchDashboard();

    /**
     * 按项目查询资源分配（甘特图数据源）
     *
     * @param initiationId 立项 ID
     * @return 资源分配列表（每条记录为一个 Map）
     */
    @GetMapping("/resource-assignments/by-initiation/{initiationId}")
    Result<List<Map<String, Object>>> listResourceAssignmentsByInitiation(
            @PathVariable("initiationId") Long initiationId);
}
