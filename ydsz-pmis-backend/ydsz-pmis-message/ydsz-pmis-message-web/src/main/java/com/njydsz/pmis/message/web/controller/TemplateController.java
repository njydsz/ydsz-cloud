package com.njydsz.pmis.message.web.controller.template;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.dto.template.TemplateAuditDTO;
import com.njydsz.pmis.message.domain.dto.template.TemplateCreateDTO;
import com.njydsz.pmis.message.domain.dto.template.TemplateQueryDTO;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.server.service.template.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息模板管理 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息模板", description = "消息模板增删改查与审核")
@RestController
@RequestMapping("/message/template")
@RequiredArgsConstructor
public class TemplateController {

    /** 消息模板服务 */
    private final TemplateService templateService;

    /**
     * 创建消息模板。
     *
     * @param dto 模板创建请求体
     * @return 统一响应结果，包含模板详情
     */
    @Operation(summary = "创建模板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_CREATE)
    @Idempotent(key = "template:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<MsgTemplateDO> create(@Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.ok(templateService.create(dto));
    }

    /**
     * 更新消息模板。
     *
     * @param id  模板 ID
     * @param dto 模板创建请求体
     * @return 统一响应结果，包含更新后模板详情
     */
    @Operation(summary = "更新模板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_UPDATE)
    @Idempotent(key = "template:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<MsgTemplateDO> update(@PathVariable String id, @Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.ok(templateService.update(id, dto));
    }

    /**
     * 删除消息模板。
     *
     * @param id 模板 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除模板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_DELETE)
    @Idempotent(key = "template:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        templateService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 统一响应结果，包含模板详情
     */
    @Operation(summary = "模板详情")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_VIEW)
    @GetMapping("/{id}")
    public BaseResponse<MsgTemplateDO> getById(@PathVariable String id) {
        return BaseResponse.ok(templateService.getById(id));
    }

    /**
     * 分页查询模板列表。
     *
     * @param query 查询参数
     * @return 统一响应结果，包含模板分页数据
     */
    @Operation(summary = "模板分页")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_LIST)
    @GetMapping("/page")
    public BaseResponse<Page<MsgTemplateDO>> page(TemplateQueryDTO query) {
        return BaseResponse.ok(templateService.page(query));
    }

    /**
     * 审核模板（通过/驳回）。
     *
     * @param id  模板 ID
     * @param dto 审核请求体
     * @return 统一响应结果
     */
    @Operation(summary = "审核模板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_APPROVE)
    @Idempotent(key = "template:audit", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/audit")
    public BaseResponse<Void> audit(@PathVariable String id, @Valid @RequestBody TemplateAuditDTO dto) {
        dto.setId(id);
        templateService.audit(id, dto);
        return BaseResponse.ok();
    }
}
