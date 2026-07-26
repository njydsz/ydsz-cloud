package com.njydsz.nextwiki.web.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.server.service.AiSummaryApplicationService;
import com.njydsz.nextwiki.server.service.StorageAnalysisApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储分析与 AI 摘要 REST API
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/analysis")
@RequiredArgsConstructor
@Tag(name = "存储分析与AI摘要", description = "存储统计报表、文档智能摘要")
public class AnalysisController {

    private final StorageAnalysisApplicationService storageAnalysisService;
    private final AiSummaryApplicationService aiSummaryService;

    @GetMapping("/overview")
    @Operation(summary = "获取存储概览")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
    public BaseResponse<StorageAnalysisApplicationService.StorageOverview> getOverview(
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(storageAnalysisService.getUserOverview(userId));
    }

    @GetMapping("/by-type")
    @Operation(summary = "按文件类型统计")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
    public BaseResponse<Map<String, StorageAnalysisApplicationService.TypeStats>> statsByType(
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(storageAnalysisService.statsByType(userId));
    }

    @GetMapping("/top-large-files")
    @Operation(summary = "大文件 Top-N")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
    public BaseResponse<List<FileNode>> topLargeFiles(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return BaseResponse.success(storageAnalysisService.topLargeFiles(userId, limit));
    }

    @PostMapping("/summary")
    @Operation(summary = "生成文档摘要")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_ANALYSIS)
    public BaseResponse<AiSummaryApplicationService.DocumentAnalysis> analyze(
            @RequestBody String content) {
        return BaseResponse.success(aiSummaryService.analyze(content));
    }
}
