package com.njydsz.pmis.nextwiki.web.controller;

import org.springframework.web.bind.annotation.*;

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
@RequestMapping("/nextwiki/preview")
@RequiredArgsConstructor
@Tag(name = "文档预览", description = "在线预览、缩略图、格式转�?)
public class PreviewController {

    private final PreviewApplicationService previewService;

    @PostMapping("/{fileNodeId}/generate")
    @Operation(summary = "生成预览（异步）")
    public BaseResponse<Void> generatePreview(@PathVariable String fileNodeId) {
        previewService.generatePreview(fileNodeId);
        return BaseResponse.ok();
    }

    @GetMapping("/supported")
    @Operation(summary = "检查文件是否支持预�?)
    public BaseResponse<Boolean> isSupported(@RequestParam String suffix) {
        return BaseResponse.ok(previewService.isPreviewSupported(suffix));
    }

    @GetMapping("/type")
    @Operation(summary = "获取预览类型")
    public BaseResponse<String> getPreviewType(@RequestParam String suffix) {
        return BaseResponse.ok(previewService.getPreviewType(suffix));
    }
}
