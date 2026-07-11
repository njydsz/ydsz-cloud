package com.njydsz.pmis.common.feign;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 执行模块 Feign 客户端（供 cronjob / 跨模块调用）
 *
 * <p>cronjob 通过此接口触发可计费利用率快照重算，
 * 避免 cronjob 直接依赖 execution 模块的具体类路径。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@FeignClient(name = "ydsz-pmis-project", fallbackFactory = ExecutionClientFallback.class)
public interface ExecutionClient {

    /**
     * 触发可计费利用率快照重算
     *
     * @param period      期间（如 2024-01），为 null 时取当前期间
     * @param recomputeAll 是否全量重算
     * @return 重算结果
     */
    @PostMapping("/execution/billableUtilization/recompute")
    Result<Map<String, Object>> recomputeBillableUtilization(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "recomputeAll", defaultValue = "false") boolean recomputeAll);

    /**
     * 健康检查
     *
     * @param period 期间，为 null 时取当前期间
     * @return 平均快照统计
     */
    @GetMapping("/execution/billableUtilization/snapshotAverage")
    Result<Map<String, Object>> snapshotAverage(@RequestParam(value = "period", required = false) String period);
}
