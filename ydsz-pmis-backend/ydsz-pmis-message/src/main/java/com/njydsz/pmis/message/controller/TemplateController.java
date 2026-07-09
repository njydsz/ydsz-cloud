package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.TemplateAuditDTO;
import com.njydsz.pmis.message.dto.TemplateCreateDTO;
import com.njydsz.pmis.message.dto.TemplateQueryDTO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.service.TemplateService;
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

    private final TemplateService templateService;

    @Operation(summary = "创建模板")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_CREATE)
    @PostMapping
    public Result<MsgTemplateDO> create(@Valid @RequestBody TemplateCreateDTO dto) {
        return Result.ok(templateService.create(dto));
    }

    @Operation(summary = "更新模板")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_UPDATE)
    @PutMapping("/{id}")
    public Result<MsgTemplateDO> update(@PathVariable String id, @Valid @RequestBody TemplateCreateDTO dto) {
        return Result.ok(templateService.update(id, dto));
    }

    @Operation(summary = "删除模板")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        templateService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "模板详情")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_VIEW)
    @GetMapping("/{id}")
    public Result<MsgTemplateDO> getById(@PathVariable String id) {
        return Result.ok(templateService.getById(id));
    }

    @Operation(summary = "模板分页")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_LIST)
    @GetMapping("/page")
    public Result<Page<MsgTemplateDO>> page(TemplateQueryDTO query) {
        return Result.ok(templateService.page(query));
    }

    @Operation(summary = "审核模板")
    @PrePermission(PermissionCodes.MESSAGE_TEMPLATE_APPROVE)
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable String id, @Valid @RequestBody TemplateAuditDTO dto) {
        dto.setId(id);
        templateService.audit(id, dto);
        return Result.ok();
    }
}
