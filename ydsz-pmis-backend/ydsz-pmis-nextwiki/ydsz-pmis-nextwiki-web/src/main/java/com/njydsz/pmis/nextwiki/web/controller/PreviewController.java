package com.njydsz.pmis.nextwiki.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.nextwiki.server.service.PreviewApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文档预览 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/preview")
@RequiredArgsConstructor
@Tag(name = "文档预览", description = "在线预览、缩略图、格式转换")
public class PreviewController {

    private final PreviewApplicationService previewService;

    @PostMapping("/{fileNodeId}/generate")
    @Operation(summary = "生成预览（异步）")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_GENERATE)
    public BaseResponse<Void> generatePreview(@PathVariable String fileNodeId) {
        previewService.generatePreview(fileNodeId);
        return BaseResponse.ok();
    }

    @GetMapping("/supported")
    @Operation(summary = "检查文件是否支持预览")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
    public BaseResponse<Boolean> isSupported(@RequestParam String suffix) {
        return BaseResponse.ok(previewService.isPreviewSupported(suffix));
    }

    @GetMapping("/type")
    @Operation(summary = "获取预览类型")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_PREVIEW_VIEW)
    public BaseResponse<String> getPreviewType(@RequestParam String suffix) {
        return BaseResponse.ok(previewService.getPreviewType(suffix));
    }
}
