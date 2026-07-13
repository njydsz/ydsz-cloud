package com.njydsz.pmis.nextwiki.web.controller;

import com.njydsz.pmis.common.core.response.Result;
import com.njydsz.pmis.nextwiki.server.service.BatchImportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 批量导入 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/import")
@RequiredArgsConstructor
@Tag(name = "批量导入", description = "批量文件上传、ZIP 导入")
public class BatchImportController {

    private final BatchImportApplicationService batchImportService;

    @PostMapping("/batch-upload")
    @Operation(summary = "批量上传文件")
    public Result<BatchImportApplicationService.BatchImportResult> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {
        return Result.ok(batchImportService.batchUpload(files, parentId, userId));
    }

    @PostMapping("/zip")
    @Operation(summary = "从 ZIP 压缩包导入")
    public Result<BatchImportApplicationService.BatchImportResult> importZip(
            @RequestParam("file") MultipartFile zipFile,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {
        return Result.ok(batchImportService.importFromZip(zipFile, parentId, userId));
    }
}
