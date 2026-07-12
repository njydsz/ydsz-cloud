package com.njydsz.pmis.agent.web.controller.tool;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.domain.dto.tool.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.domain.dto.tool.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.server.service.tool.PromptTemplateService;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.core.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板管理接口（P2-2 落地）。
 *
 * <p>对标 Coze / Dify 的 Prompt 管理后台，提供模板的 CRUD 与版本激活能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Slf4j
@RestController
@RequestMapping("/agent/promptTemplate")
@RequiredArgsConstructor
@Tag(name = "Prompt 模板管理", description = "Agent Prompt 模板的创建、查询、激活与删除")
public class PromptTemplateController {

    /** Prompt 模板服务 */
    private final PromptTemplateService service;

    /**
     * 创建模板（默认非生效，需手动激活）。
     *
     * @param dto 模板创建参数
     * @return 落库后的模板
     */
    @Idempotent(key = "promptTemplate:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建模板", description = "创建新的 Prompt 模板（默认非生效，需手动激活）")
    public BaseResponse<AgentPromptTemplateDO> create(@Valid @RequestBody PromptTemplateCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 激活模板（同 code 的其他版本自动失效）。
     *
     * @param id 模板 ID
     * @return 激活后的模板
     */
    @Idempotent(key = "promptTemplate:activate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/activate")
    @Operation(summary = "激活模板", description = "激活指定模板版本，同 code 的其他版本自动失效")
    public BaseResponse<AgentPromptTemplateDO> activate(@PathVariable String id) {
        return BaseResponse.ok(service.activate(id));
    }

    /**
     * 查询模板详情。
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    public BaseResponse<AgentPromptTemplateDO> getById(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询模板。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询模板")
    public BaseResponse<PageResponse<AgentPromptTemplateDO>> page(PromptTemplateQueryDTO query) {
        return BaseResponse.ok(service.page(query));
    }

    /**
     * 删除模板（软删除）。
     *
     * @param id 模板 ID
     * @return 空响应
     */
    @Idempotent(key = "promptTemplate:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "软删除指定模板")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }
}
