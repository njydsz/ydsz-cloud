package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.template.TemplatePreviewDTO;
import com.njydsz.pmis.message.dto.template.TemplateTestSendDTO;
import com.njydsz.pmis.message.entity.template.MsgTemplateVersionDO;
import com.njydsz.pmis.message.service.template.TemplateVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模板版本管理与可视化 Controller。
 *
 * <p>P1-6: 提供模板版本历史查询、版本回滚、模板预览（渲染）和模板试发接口。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Tag(name = "模板版本管理", description = "版本历史、回滚、预览、试发")
@RestController
@RequestMapping("/template/version")
@RequiredArgsConstructor
public class TemplateVersionController {

    /** 模板版本管理服务 */
    private final TemplateVersionService templateVersionService;

    /**
     * 查询模板版本历史。
     *
     * @param templateCode 模板编码
     * @return 统一响应结果，包含版本列表
     */
    @Operation(summary = "查询模板版本历史")
    @PrePermission(PermissionCodes.NOTIF_TEMPLATE_VIEW)
    @GetMapping("/list/{templateCode}")
    public Result<List<MsgTemplateVersionDO>> listVersions(@PathVariable String templateCode) {
        return Result.ok(templateVersionService.listVersions(templateCode));
    }

    /**
     * 回滚到指定版本。
     *
     * @param templateCode 模板编码
     * @param version      目标版本号
     * @return 统一响应结果，包含新版本 ID
     */
    @Operation(summary = "回滚到指定版本")
    @PrePermission(PermissionCodes.NOTIF_TEMPLATE_AUDIT)
    @Idempotent(key = "template-version:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rollback")
    public Result<String> rollback(@RequestParam String templateCode, @RequestParam int version) {
        return Result.ok(templateVersionService.rollbackToVersion(templateCode, version));
    }

    /**
     * 预览模板渲染结果。
     *
     * @param dto 预览请求体
     * @return 统一响应结果，包含渲染后的内容
     */
    @Operation(summary = "预览模板渲染结果")
    @PrePermission(PermissionCodes.NOTIF_TEMPLATE_VIEW)
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/preview")
    public Result<String> preview(@Valid @RequestBody TemplatePreviewDTO dto) {
        if (dto == null) {
            return Result.failed(BizErrorCode.BAD_REQUEST, "预览参数为空");
        }
        return Result.ok(templateVersionService.preview(dto));
    }

    /**
     * 试发模板（向测试接收人发送）。
     *
     * @param dto 试发请求体
     * @return 统一响应结果，包含发送结果
     */
    @Operation(summary = "试发模板（向测试接收人发送）")
    @PrePermission(PermissionCodes.NOTIF_TEMPLATE_AUDIT)
    @Idempotent(key = "template-version:test-send", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/test-send")
    public Result<MessageResult> testSend(@Valid @RequestBody TemplateTestSendDTO dto) {
        if (dto == null) {
            return Result.failed(BizErrorCode.BAD_REQUEST, "试发参数为空");
        }
        return Result.ok(templateVersionService.testSend(dto));
    }
}
