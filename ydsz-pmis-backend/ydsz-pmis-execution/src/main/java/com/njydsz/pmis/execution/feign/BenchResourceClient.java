package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.R;
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
@FeignClient(name = "ydsz-pmis-user", fallbackFactory = BenchResourceClientFallback.class)
public interface BenchResourceClient {

    /**
     * Bench 仪表盘汇总（活跃池分布 + 累计闲置成本）
     */
    @GetMapping("/api/v1/bench/dashboard")
    Result<Map<String, Object>> getBenchDashboard();

    /**
     * 按项目查询资源分配（甘特图数据源）
     */
    @GetMapping("/api/v1/resource-assignments/by-initiation/{initiationId}")
    Result<List<Map<String, Object>>> listResourceAssignmentsByInitiation(
            @PathVariable("initiationId") Long initiationId);
}
