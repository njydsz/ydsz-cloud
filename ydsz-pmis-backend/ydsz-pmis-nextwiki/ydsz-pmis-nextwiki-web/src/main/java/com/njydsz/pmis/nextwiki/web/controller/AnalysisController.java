package com.njydsz.pmis.nextwiki.web.controller;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.nextwiki.server.service.AiSummaryApplicationService;
import com.njydsz.pmis.nextwiki.server.service.StorageAnalysisApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储分析与 AI 摘要 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/analysis")
@RequiredArgsConstructor
@Tag(name = "存储分析与AI摘要", description = "存储统计报表、文档智能摘要")
public class AnalysisController {

    private final StorageAnalysisApplicationService storageAnalysisService;
    private final AiSummaryApplicationService aiSummaryService;

    @GetMapping("/overview")
    @Operation(summary = "获取存储概览")
    public Result<StorageAnalysisApplicationService.StorageOverview> getOverview(
            @RequestHeader("X-User-Id") String userId) {
        return Result.ok(storageAnalysisService.getUserOverview(userId));
    }

    @GetMapping("/by-type")
    @Operation(summary = "按文件类型统计")
    public Result<java.util.Map<String, StorageAnalysisApplicationService.TypeStats>> statsByType(
            @RequestHeader("X-User-Id") String userId) {
        return Result.ok(storageAnalysisService.statsByType(userId));
    }

    @GetMapping("/top-large-files")
    @Operation(summary = "大文件 Top-N")
    public Result<java.util.List<?>> topLargeFiles(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(storageAnalysisService.topLargeFiles(userId, limit));
    }

    @PostMapping("/summary")
    @Operation(summary = "生成文档摘要")
    public Result<AiSummaryApplicationService.DocumentAnalysis> analyze(@RequestBody String content) {
        return Result.ok(aiSummaryService.analyze(content));
    }
}
