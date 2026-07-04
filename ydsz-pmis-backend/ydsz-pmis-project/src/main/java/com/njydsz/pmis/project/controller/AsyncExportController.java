package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.project.service.AsyncExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异步导出 Controller（下载中心）。
 *
 * <p>提供异步导出任务提交、记录查询、下载 URL 获取与记录删除能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/execution/export")
@RequiredArgsConstructor
@Tag(name = "异步导出", description = "异步导出任务管理与下载中心")
public class AsyncExportController {

    private final AsyncExportService asyncExportService;

    @PostMapping("/submit")
    @Operation(summary = "提交异步导出任务")
    @RateLimit(key = "export", qps = 3, windowSeconds = 60,
            message = "{validation.execution.msg_54683c1c}")
    public Map<String, Object> submitExport(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam String exportType,
            @RequestBody(required = false) Map<String, Object> params) {
        Long recordId = asyncExportService.submitExport(userId, exportType, params != null ? params : Map.of());
        return Map.of("recordId", recordId, "status", "PENDING");
    }

    @GetMapping("/records")
    @Operation(summary = "查询导出记录列表")
    public Page<Map<String, Object>> getExportRecords(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return asyncExportService.getExportRecords(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, AsyncExportService.COL_CREATED_AT)));
    }

    @GetMapping("/{recordId}/download")
    @Operation(summary = "获取下载URL")
    public Map<String, Object> getDownloadUrl(@PathVariable Long recordId) {
        String url = asyncExportService.getDownloadUrl(recordId);
        return Map.of("url", url != null ? url : "", "success", url != null);
    }

    @DeleteMapping("/{recordId}")
    @Operation(summary = "删除导出记录")
    public Map<String, Object> deleteExportRecord(@PathVariable Long recordId) {
        asyncExportService.deleteExportRecord(recordId);
        return Map.of("success", true);
    }
}
