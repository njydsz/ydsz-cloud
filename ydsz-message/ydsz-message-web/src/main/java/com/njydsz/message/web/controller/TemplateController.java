package com.njydsz.message.web.controller.template;

import java.util.List;

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
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.template.TemplateAuditDTO;
import com.njydsz.message.domain.dto.template.TemplateCreateDTO;
import com.njydsz.message.domain.dto.template.TemplateQueryDTO;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.domain.vo.MsgTemplateVO;
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
 * <p>提供消息模板的<b>全生命周期管理</b> HTTP API：创建 / 查询 / 编辑 / 删除 / 审核 / 上下线，
 * 是 ydsz-message 模块「模板中心」的入口。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/template/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@code POST /} 创建 / {@code PUT /{id}} 编辑 / {@code DELETE /{id}} 删除（仅 DRAFT 状态可删）</li>
 *   <li><b>分页查询</b>：{@code GET /page} 支持按渠道 / 状态 / 关键字多维过滤</li>
 *   <li><b>审核流</b>：{@code POST /audit} 模板审核（PASS / REJECT）</li>
 *   <li><b>版本管理</b>：通过 {@code TemplateVersionController} 实现模板版本化</li>
 *   <li><b>预览</b>：通过 {@code TemplatePreviewController} 实时预览模板渲染结果</li>
 * </ul>
 *
 * <p><b>模板状态机：</b>{@code DRAFT}（待审核）→ {@code PUBLISHED}（已发布，可用于发送）→
 * {@code OFFLINE}（已下线，停止使用）/ {@code REJECTED}（审核未通过）。
 *
 * <p><b>变量替换：</b>模板内容支持 {@code ${var}} 嵌套变量语法，发送时由 {@code TemplateEngine} 替换为实际值。
 * 例如：{@code "您的验证码为 ${code}，5 分钟内有效"} → {@code "您的验证码为 123456，5 分钟内有效"}。
 *
 * <p><b>多渠道支持：</b>同一模板可绑定到多个渠道（短信 / 邮件 / 站内信 / 钉钉 / 飞书 / 企业微信），
 * 每个渠道有独立的 {@code TemplateCode} 与供应商模板 ID。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 5s 防重（Redis SET NX EX）</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流</li>
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_TEMPLATE_MANAGE} 等权限码</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.template.TemplateService 模板服务
 * @see com.njydsz.message.domain.entity.template.MsgTemplate 模板实体
 */
@Tag(name = "消息模板", description = "消息模板增删改查与审核")
@RestController
@RequestMapping("/api/v1/message/template")
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
    public BaseResponse<MsgTemplateVO> create(@Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(templateService.create(dto)));
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
    public BaseResponse<MsgTemplateVO> update(@PathVariable String id, @Valid @RequestBody TemplateCreateDTO dto) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(templateService.update(id, dto)));
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
    public BaseResponse<MsgTemplateVO> getById(@PathVariable String id) {
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(templateService.getById(id)));
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
    public PageResponse<List<MsgTemplateVO>> page(TemplateQueryDTO query) {
        Page<MsgTemplate> page = templateService.page(query);
        return PageResponses.success(page, MessageConverter.INSTANT::entityToVO);
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
