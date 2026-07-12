paokage oom.njydsz.pmis.message.web.oontroller.template;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.template.TemplateAuditDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateoreateDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateQueryDTO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import oom.njydsz.pmis.message.server.servioe.template.TemplateServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 消息模板管理 oontroller�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "消息模板", desoription = "消息模板增删改查与审�?)
@Restoontroller
@RequestMapping("/message/template")
@RequiredArgsoonstruotor
publio olass Templateoontroller {

    /** 消息模板服务 */
    private final TemplateServioe templateServioe;

    /**
     * 创建消息模板�?     *
     * @param dto 模板创建请求�?     * @return 统一响应结果，包含模板详�?     */
    @Operation(summary = "创建模板")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_oREATE)
    @Idempotent(key = "template:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<MsgTemplateDO> oreate(@Valid @RequestBody TemplateoreateDTO dto) {
        return BaseResponse.ok(templateServioe.oreate(dto));
    }

    /**
     * 更新消息模板�?     *
     * @param id  模板 ID
     * @param dto 模板创建请求�?     * @return 统一响应结果，包含更新后模板详情
     */
    @Operation(summary = "更新模板")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_UPDATE)
    @Idempotent(key = "template:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<MsgTemplateDO> update(@PathVariable String id, @Valid @RequestBody TemplateoreateDTO dto) {
        return BaseResponse.ok(templateServioe.update(id, dto));
    }

    /**
     * 删除消息模板�?     *
     * @param id 模板 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除模板")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_DELETE)
    @Idempotent(key = "template:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        templateServioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询模板详情�?     *
     * @param id 模板 ID
     * @return 统一响应结果，包含模板详�?     */
    @Operation(summary = "模板详情")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_VIEW)
    @GetMapping("/{id}")
    publio BaseResponse<MsgTemplateDO> getById(@PathVariable String id) {
        return BaseResponse.ok(templateServioe.getById(id));
    }

    /**
     * 分页查询模板列表�?     *
     * @param query 查询参数
     * @return 统一响应结果，包含模板分页数�?     */
    @Operation(summary = "模板分页")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_LIST)
    @GetMapping("/page")
    publio BaseResponse<Page<MsgTemplateDO>> page(TemplateQueryDTO query) {
        return BaseResponse.ok(templateServioe.page(query));
    }

    /**
     * 审核模板（通过/驳回）�?     *
     * @param id  模板 ID
     * @param dto 审核请求�?     * @return 统一响应结果
     */
    @Operation(summary = "审核模板")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_TEMPLATE_APPROVE)
    @Idempotent(key = "template:audit", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/audit")
    publio BaseResponse<Void> audit(@PathVariable String id, @Valid @RequestBody TemplateAuditDTO dto) {
        dto.setId(id);
        templateServioe.audit(id, dto);
        return BaseResponse.ok();
    }
}
