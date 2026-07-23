package com.njydsz.message.web.controller.template;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.template.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.template.TemplateTestSendDTO;
import com.njydsz.message.domain.entity.template.MsgTemplateVersionDO;
import com.njydsz.message.server.service.template.TemplateVersionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 模板版本管理与可视化 Controller。
 *
 * <p>P1-6: 提供模板版本历史查询、版本回滚、模板预览（渲染）和模板试发接口。
 *
 * @author ydsz-team
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
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_VIEW)
    @GetMapping("/list/{templateCode}")
    public BaseResponse<List<MsgTemplateVersionDO>> listVersions(@PathVariable String templateCode) {
        return BaseResponse.success(templateVersionService.listVersions(templateCode));
    }

    /**
     * 回滚到指定版本。
     *
     * @param templateCode 模板编码
     * @param version      目标版本号
     * @return 统一响应结果，包含新版本 ID
     */
    @Operation(summary = "回滚到指定版本")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_AUDIT)
    @Idempotent(key = "templateVersion:rollback", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/rollback")
    public BaseResponse<String> rollback(@RequestParam String templateCode, @RequestParam int version) {
        return BaseResponse.success(templateVersionService.rollbackToVersion(templateCode, version));
    }

    /**
     * 预览模板渲染结果。
     *
     * @param dto 预览请求体
     * @return 统一响应结果，包含渲染后的内容
     */
    @Operation(summary = "预览模板渲染结果")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_VIEW)
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/preview")
    public BaseResponse<String> preview(@Valid @RequestBody TemplatePreviewDTO dto) {
        if (dto == null) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "预览参数为空");
        }
        return BaseResponse.success(templateVersionService.preview(dto));
    }

    /**
     * 试发模板（向测试接收人发送）。
     *
     * @param dto 试发请求体
     * @return 统一响应结果，包含发送结果
     */
    @Operation(summary = "试发模板（向测试接收人发送）")
    @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_AUDIT)
    @Idempotent(key = "templateVersion:testSend", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/testSend")
    public BaseResponse<MessageResult> testSend(@Valid @RequestBody TemplateTestSendDTO dto) {
        if (dto == null) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "试发参数为空");
        }
        return BaseResponse.success(templateVersionService.testSend(dto));
    }
}
