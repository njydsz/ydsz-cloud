package com.njydsz.message.web.controller.template;

import jakarta.validation.Valid;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.template.TemplateAuditDTO;
import com.njydsz.message.domain.dto.template.TemplateCreateDTO;
import com.njydsz.message.domain.dto.template.TemplateQueryDTO;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.server.service.template.TemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 消息模板管理 Controller。
 *
 * @author ydsz-team
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
    @Idempotent(key = "ydsz:message:TemplateController:create:lock", ttlSeconds = 5)
    @Audit(module = "模板管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @RateLimit(resource = "message.template.create", threshold = 50)
    @PostMapping
    public BaseResponse<MsgTemplate> create(@Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.success(templateService.create(dto));
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
    @Idempotent(key = "ydsz:message:TemplateController:update:lock", ttlSeconds = 5)
    @Audit(module = "模板管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "message.template.update", threshold = 50)
    @PutMapping("/{id}")
    public BaseResponse<MsgTemplate> update(@PathVariable String id, @Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.success(templateService.update(id, dto));
    }

    /**
     * 删除消息模板。
     *
     * @param id 模板 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除模板")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_TEMPLATE_DELETE)
    @Idempotent(key = "ydsz:message:TemplateController:delete:lock", ttlSeconds = 5)
    @Audit(module = "模板管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @RateLimit(resource = "message.template.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        templateService.delete(id);
        return BaseResponse.success();
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
    public BaseResponse<MsgTemplate> getById(@PathVariable String id) {
        return BaseResponse.success(templateService.getById(id));
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
    public BaseResponse<Page<MsgTemplate>> page(TemplateQueryDTO query) {
        return BaseResponse.success(templateService.page(query));
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
    @Idempotent(key = "ydsz:message:TemplateController:audit:lock", ttlSeconds = 5)
    @Audit(module = "模板管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'audit'")
    @RateLimit(resource = "message.template.audit", threshold = 50)
    @PostMapping("/{id}/audit")
    public BaseResponse<Void> audit(@PathVariable String id, @Valid @RequestBody TemplateAuditDTO dto) {
        dto.setId(id);
        templateService.audit(id, dto);
        return BaseResponse.success();
    }
}
