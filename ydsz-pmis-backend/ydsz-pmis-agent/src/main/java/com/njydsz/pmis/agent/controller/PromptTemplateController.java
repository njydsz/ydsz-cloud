package com.njydsz.pmis.agent.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.dto.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.dto.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.entity.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.service.PromptTemplateService;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
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
@RequestMapping("/agent/prompt-template")
@RequiredArgsConstructor
@Tag(name = "Prompt 模板管理", description = "Agent Prompt 模板的创建、查询、激活与删除")
public class PromptTemplateController {

    /** Prompt 模板服务 */
    private final PromptTemplateService service;

    @Idempotent(key = "prompt-template:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建模板", description = "创建新的 Prompt 模板（默认非生效，需手动激活）")
    public Result<AgentPromptTemplateDO> create(@Valid @RequestBody PromptTemplateCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Idempotent(key = "prompt-template:activate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/activate")
    @Operation(summary = "激活模板", description = "激活指定模板版本，同 code 的其他版本自动失效")
    public Result<AgentPromptTemplateDO> activate(@PathVariable String id) {
        return Result.ok(service.activate(id));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    public Result<AgentPromptTemplateDO> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    @GetMapping
    @Operation(summary = "分页查询模板")
    public Result<PageResult<AgentPromptTemplateDO>> page(PromptTemplateQueryDTO query) {
        return Result.ok(service.page(query));
    }

    @Idempotent(key = "prompt-template:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "软删除指定模板")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }
}
