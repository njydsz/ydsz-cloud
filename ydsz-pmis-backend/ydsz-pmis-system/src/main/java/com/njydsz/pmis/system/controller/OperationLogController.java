package com.njydsz.pmis.system.controller;

import com.njydsz.pmis.system.entity.OperationLogDO;
import com.njydsz.pmis.system.service.OperationLogServiceImpl;
import com.njydsz.pmis.system.util.DiffCalculator;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.entity.CursorPageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/audit/operation")
@RequiredArgsConstructor
@Validated
public class OperationLogController {

    /** 操作日志服务 */
    private final OperationLogServiceImpl service;

    /**
     * 分页查询操作日志
     *
     * @param page      页码
     * @param size      每页大小
     * @param userId    用户 ID（可选）
     * @param bizType   业务类型（可选）
     * @param status    状态（可选）
     * @param module    模块名（可选）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/page")
    public Result<PageResult<OperationLogDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        return Result.ok(PageResult.ofPage(service.page(page, size, userId, bizType, status, module, startTime, endTime)));
    }

    /**
     * 游标分页查询操作日志（P2-8 深翻优化）
     *
     * <p>使用 keyset pagination 替代 OFFSET，适用于审计日志等大表深翻场景。
     * 首次请求不传 cursor，后续请求传入上一次返回的 nextCursor。
     *
     * @param size      每页大小（默认 20，最大 200）
     * @param cursor    游标（首次请求不传）
     * @param userId    用户 ID（可选）
     * @param bizType   业务类型（可选）
     * @param status    状态（可选）
     * @param module    模块名（可选）
     * @param startTime 起始时间（可选）
     * @param endTime   截止时间（可选）
     * @return 游标分页结果
     */
    @Operation(summary = "游标分页查询（深翻优化）")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/cursor-page")
    public Result<CursorPageResult<OperationLogDO>> cursorPage(
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        return Result.ok(service.pageByCursor(size, cursor, userId, bizType, status, module, startTime, endTime));
    }

    /**
     * 按用户查询操作日志
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 统一响应结果，包含操作日志列表
     */
    @Operation(summary = "按用户查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/by-user")
    public Result<List<OperationLogDO>> byUser(@RequestParam Long userId,
                                          @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.listByUser(userId, limit));
    }

    /**
     * 按业务查询操作日志
     *
     * @param bizType 业务类型
     * @param bizId   业务单据 ID
     * @param limit   最大条数
     * @return 统一响应结果，包含操作日志列表
     */
    @Operation(summary = "按业务查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/by-biz")
    public Result<List<OperationLogDO>> byBiz(@RequestParam String bizType,
                                         @RequestParam String bizId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.listByBiz(bizType, bizId, limit));
    }

    /**
     * 清理 N 天前的操作日志
     *
     * @param days 保留天数
     * @return 统一响应结果，包含删除条数
     */
    @Operation(summary = "清理 N 天前日志")
    @PrePermission(PermissionCodes.AUDIT_LOG_CLEAN)
    @PostMapping("/clean")
    public Result<Integer> clean(@RequestParam(defaultValue = "90") int days) {
        return Result.ok(service.cleanBefore(days));
    }

    /**
     * 查询操作日志的字段级变更差异
     *
     * @param id 操作日志 ID
     * @return 字段差异列表
     */
    @Operation(summary = "查询变更差异")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/{id}/diff")
    public List<DiffCalculator.FieldDiff> getDiff(@PathVariable @Min(1) Long id) {
        OperationLogDO log = service.getById(id);
        if (log == null) return List.of();
        return DiffCalculator.calculateDiff(log.getBeforeData(), log.getAfterData());
    }
}
