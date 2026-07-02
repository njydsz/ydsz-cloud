package com.njydsz.pmis.audit.controller;

import com.njydsz.pmis.audit.entity.OperationLogDO;
import com.njydsz.pmis.audit.service.OperationLogServiceImpl;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class OperationLogController {

    private final OperationLogServiceImpl service;

    @Operation(summary = "分页查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/page")
    public Result<PageResult<OperationLogDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
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

    @Operation(summary = "按用户查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/by-user")
    public Result<List<OperationLogDO>> byUser(@RequestParam Long userId,
                                          @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.listByUser(userId, limit));
    }

    @Operation(summary = "按业务查询")
    @PrePermission(PermissionCodes.AUDIT_LOG_VIEW)
    @GetMapping("/by-biz")
    public Result<List<OperationLogDO>> byBiz(@RequestParam String bizType,
                                         @RequestParam String bizId,
                                         @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(service.listByBiz(bizType, bizId, limit));
    }

    @Operation(summary = "清理 N 天前日志")
    @PrePermission(PermissionCodes.AUDIT_LOG_CLEAN)
    @PostMapping("/clean")
    public Result<Integer> clean(@RequestParam(defaultValue = "90") int days) {
        return Result.ok(service.cleanBefore(days));
    }
}
