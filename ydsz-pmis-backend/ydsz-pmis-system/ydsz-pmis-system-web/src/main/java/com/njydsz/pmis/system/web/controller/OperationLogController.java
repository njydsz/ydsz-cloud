package com.njydsz.pmis.system.web.controller.audit;

import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;

import com.njydsz.pmis.system.domain.entity.audit.OperationLogDO;
import com.njydsz.pmis.system.server.service.audit.OperationLogServiceImpl;
import com.njydsz.pmis.system.server.util.DiffCalculator;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.domain.query.CursorPageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "操作日志", description = "操作审计日志查询与管理接口")
@RestController
@RequestMapping("/audit/operation")
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
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/page")
    public BaseResponse<PageResponse<OperationLogDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
            @Parameter(description = "业务类型") @RequestParam(required = false) String bizType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "模块名") @RequestParam(required = false) String module,
            @Parameter(description = "起始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @Parameter(description = "截止时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        return BaseResponse.ok(PageResponse.ofPage(service.page(page, size, userId, bizType, status, module, startTime, endTime)));
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
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/cursorPage")
    public BaseResponse<CursorPageResult<OperationLogDO>> cursorPage(
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "游标（首次请求不传）") @RequestParam(required = false) String cursor,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
            @Parameter(description = "业务类型") @RequestParam(required = false) String bizType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "模块名") @RequestParam(required = false) String module,
            @Parameter(description = "起始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @Parameter(description = "截止时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        return BaseResponse.ok(service.pageByCursor(size, cursor, userId, bizType, status, module, startTime, endTime));
    }

    /**
     * 按用户查询操作日志
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 统一响应结果，包含操作日志列表
     */
    @Operation(summary = "按用户查询")
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/byUser")
    public BaseResponse<List<OperationLogDO>> byUser(
            @Parameter(description = "用户ID") @RequestParam String userId,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(service.listByUser(userId, limit));
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
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/byBiz")
    public BaseResponse<List<OperationLogDO>> byBiz(
            @Parameter(description = "业务类型") @RequestParam String bizType,
            @Parameter(description = "业务单据ID") @RequestParam String bizId,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(service.listByBiz(bizType, bizId, limit));
    }

    /**
     * 清理 N 天前的操作日志
     *
     * @param days 保留天数
     * @return 统一响应结果，包含删除条数
     */
    @Operation(summary = "清理 N 天前日志")
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_CLEAN)
    @OperationLog(module = "操作日志", action = "清理历史日志", bizType = "AUDIT_LOG", saveParams = true)
    @IdempotentExempt("审计清理接口，无需幂等")
    @PostMapping("/clean")
    public BaseResponse<Integer> clean(
            @Parameter(description = "保留天数") @RequestParam(defaultValue = "90") int days) {
        return BaseResponse.ok(service.cleanBefore(days));
    }

    /**
     * 查询操作日志的字段级变更差异
     *
     * @param id 操作日志 ID
     * @return 字段差异列表
     */
    @Operation(summary = "查询变更差异")
    @AuthApiPermission(apiCodes = PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/{id}/diff")
    public List<DiffCalculator.FieldDiff> getDiff(
            @Parameter(description = "操作日志ID") @PathVariable String id) {
        OperationLogDO log = service.getById(id);
        if (log == null) return List.of();
        return DiffCalculator.calculateDiff(log.getBeforeData(), log.getAfterData());
    }
}
