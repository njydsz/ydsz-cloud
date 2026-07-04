package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.service.FlowHistoryArchiveService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流程历史数据归档管理 API
 *
 * <p>P2-8：提供手动触发归档、手动清理冷数据、查询归档配置等运维能力，
 * 避免每次都需要等待 cron 触发或修改 pmis_job 表。
 *
 * <p>典型场景：
 * <ul>
 *   <li>磁盘空间告急时手动触发 purge 清理 1 年前的归档数据</li>
 *   <li>上线前验证归档逻辑时手动触发一次 archive</li>
 *   <li>运维查看当前生效的归档配置（无需登录服务器查看 yml）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "流程历史归档")
@RestController
@RequestMapping("/api/v1/workflow/history")
@RequiredArgsConstructor
@Validated
public class FlowHistoryArchiveController {

    private final FlowHistoryArchiveService archiveService;

    /**
     * 查询当前归档配置
     *
     * @return 配置项 Map
     */
    @Operation(summary = "查询归档配置")
    @GetMapping("/config")
    public Result<Map<String, Object>> getConfig() {
        return Result.ok(archiveService.getArchiveConfig());
    }

    /**
     * 手动触发归档
     *
     * <p>参数可选，未传则使用 {@code application.yml} 配置的默认值。
     * 适用于临时归档更早的数据（如手动归档 90 天前的数据）。
     *
     * @param retentionDays 归档阈值天数（可选）
     * @param batchSize     单次批量大小（可选）
     * @param maxProcessMs  单次最大耗时毫秒（可选）
     * @return 执行结果摘要
     */
    @Operation(summary = "手动触发归档")
    @PostMapping("/archive")
    public Result<Map<String, Object>> archive(@RequestParam(required = false) Integer retentionDays,
                                                  @RequestParam(required = false) Integer batchSize,
                                                  @RequestParam(required = false) Long maxProcessMs) {
        log.info("[FlowHistoryArchiveController] 手动触发归档 retentionDays={} batchSize={} maxProcessMs={}",
                retentionDays, batchSize, maxProcessMs);
        return Result.ok(archiveService.archive(retentionDays, batchSize, maxProcessMs));
    }

    /**
     * 手动触发清理（purge）
     *
     * <p>清理归档表中超过阈值的冷数据，回收存储空间。
     * 即使配置 {@code purge-enabled=false}，本接口仍可强制执行（参数优先于配置）。
     *
     * @param purgeDays 清理阈值天数（可选，默认使用配置值）
     * @return 执行结果摘要
     */
    @Operation(summary = "手动触发清理（purge）")
    @PostMapping("/purge")
    public Result<Map<String, Object>> purge(@RequestParam(required = false) Integer purgeDays) {
        log.info("[FlowHistoryArchiveController] 手动触发清理 purgeDays={}", purgeDays);
        return Result.ok(archiveService.purge(purgeDays));
    }
}
