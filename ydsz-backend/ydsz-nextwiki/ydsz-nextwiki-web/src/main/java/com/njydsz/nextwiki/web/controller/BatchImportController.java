package com.njydsz.nextwiki.web.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.server.service.BatchImportApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量导入 REST API
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/import")
@RequiredArgsConstructor
@Tag(name = "批量导入", description = "批量文件上传、ZIP 导入")
public class BatchImportController {

    private final BatchImportApplicationService batchImportService;

    @PostMapping("/batch-upload")
    @Operation(summary = "批量上传文件")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_BATCH_IMPORT)
    public BaseResponse<BatchImportApplicationService.BatchImportResult> batchUpload(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(batchImportService.batchUpload(files, parentId, userId));
    }

    @PostMapping("/zip")
    @Operation(summary = "从 ZIP 压缩包导入")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_BATCH_IMPORT)
    public BaseResponse<BatchImportApplicationService.BatchImportResult> importZip(
            @RequestParam("file") MultipartFile zipFile,
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestHeader("X-User-Id") String userId) {
        return BaseResponse.success(batchImportService.importFromZip(zipFile, parentId, userId));
    }
}
