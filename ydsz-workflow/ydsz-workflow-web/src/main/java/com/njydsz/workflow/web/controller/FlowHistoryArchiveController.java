package com.njydsz.workflow.web.controller.analytics;

import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.server.service.FlowHistoryArchiveService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程历史数据归档管理 API（P2-8）
 *
 * <p>提供手动触发归档、手动清理冷数据、查询归档配置等运维能力，
 * 避免每次都需要等待 cron 触发或修改 ydsz_job 表。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/history/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>归档触发</b>：{@code POST /archive} — 手动触发一次归档（将历史实例 / 任务迁到归档表）</li>
 *   <li><b>清理冷数据</b>：{@code POST /purge} — 手动清理 N 个月前的归档数据（释放磁盘）</li>
 *   <li><b>配置查询</b>：{@code GET /config} — 查询当前生效的归档配置（无需登录服务器）</li>
 *   <li><b>统计查询</b>：{@code GET /stats} — 各状态实例数量 / 归档前 N 条记录</li>
 * </ul>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>磁盘空间告急时手动触发 {@code /purge} 清理 1 年前的归档数据</li>
 *   <li>上线前验证归档逻辑时手动触发一次 {@code /archive}</li>
 *   <li>运维查看当前生效的归档配置（无需登录服务器查看 yml）</li>
 *   <li>周期性归档出现异常时手动补跑</li>
 * </ul>
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（archive / purge）启用 {@link Idempotent} 5s 防重</li>
 *   <li>purge 操作为<b>不可逆</b>操作，建议通过细粒度权限码控制（如 {@code workflow:history:purge}）</li>
 *   <li>大批量归档 / 清理走游标分页（{@code id > lastId} + {@code LIMIT 1000}），避免长事务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowHistoryArchiveService 归档服务
 */
@Slf4j
@Tag(name = "流程历史归档")
@RestController
@RequestMapping("/api/v1/workflow/history")
@RequiredArgsConstructor
@Validated
public class FlowHistoryArchiveController {

    /** 流程历史归档服务，负责数据归档、冷数据清理与配置查询 */
    private final FlowHistoryArchiveService archiveService;

    /**
     * 查询当前归档配置
     *
     * @return 配置项 Map
     */
    @Operation(summary = "查询归档配置")
    @GetMapping("/config")
    public BaseResponse<Map<String, Object>> getConfig() {
        return BaseResponse.success(archiveService.getArchiveConfig());
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
    @Audit(module = "历史归档", type = AuditType.OPERATION, action = AuditAction.BACKUP, content = "'archive'")
    @Operation(summary = "手动触发归档")
    @Idempotent(key = "ydsz:workflow:FlowHistoryArchiveController:archive:lock", ttlSeconds = 5)
    @PostMapping("/archive")
    public BaseResponse<Map<String, Object>> archive(@RequestParam(required = false) @Min(1) Integer retentionDays,
                                                  @RequestParam(required = false) @Min(1) @Max(1000) Integer batchSize,
                                                  @RequestParam(required = false) Long maxProcessMs) {
        log.info("[FlowHistoryArchiveController] 手动触发归档 retentionDays={} batchSize={} maxProcessMs={}",
                retentionDays, batchSize, maxProcessMs);
        return BaseResponse.success(archiveService.archive(retentionDays, batchSize, maxProcessMs));
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
    @Audit(module = "历史归档", type = AuditType.OPERATION, action = AuditAction.CLEAN, content = "'purge'")
    @Operation(summary = "手动触发清理（purge）")
    @Idempotent(key = "ydsz:workflow:FlowHistoryArchiveController:purge:lock", ttlSeconds = 5)
    @PostMapping("/purge")
    public BaseResponse<Map<String, Object>> purge(@RequestParam(required = false) Integer purgeDays) {
        log.info("[FlowHistoryArchiveController] 手动触发清理 purgeDays={}", purgeDays);
        return BaseResponse.success(archiveService.purge(purgeDays));
    }
}
