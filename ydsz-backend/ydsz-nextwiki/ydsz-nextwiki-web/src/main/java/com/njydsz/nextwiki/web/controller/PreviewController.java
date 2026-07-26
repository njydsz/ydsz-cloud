package com.njydsz.nextwiki.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.nextwiki.server.service.PreviewApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 文档预览 REST API
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/preview")
@RequiredArgsConstructor
@Tag(name = "文档预览", description = "在线预览、缩略图、格式转换")
public class PreviewController {

    private final PreviewApplicationService previewService;

    @Idempotent(key = "nextwiki:preview:generatePreview", ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "nextwiki.preview.generatePreview", threshold = 50)
    @SentinelRateLimit(resource = "nextwiki.preview.generatePreview", threshold = 50)
    @PostMapping("/{fileNodeId}/generate")
    @Operation(summary = "生成预览（异步）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_GENERATE)
    public BaseResponse<Void> generatePreview(@PathVariable String fileNodeId) {
        previewService.generatePreview(fileNodeId);
        return BaseResponse.success();
    }

    @GetMapping("/supported")
    @Operation(summary = "检查文件是否支持预览")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
    public BaseResponse<Boolean> isSupported(@RequestParam String suffix) {
        return BaseResponse.success(previewService.isPreviewSupported(suffix));
    }

    @GetMapping("/type")
    @Operation(summary = "获取预览类型")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
    public BaseResponse<String> getPreviewType(@RequestParam String suffix) {
        return BaseResponse.success(previewService.getPreviewType(suffix));
    }
}
