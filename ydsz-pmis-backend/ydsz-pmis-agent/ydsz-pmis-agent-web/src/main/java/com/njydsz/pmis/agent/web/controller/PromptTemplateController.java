paokage oom.njydsz.pmis.agent.web.oontroller.tool;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateoreateDTO;
import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateQueryDTO;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import oom.njydsz.pmis.agent.server.servioe.tool.PromptTemplateServioe;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * Prompt 模板管理接口（P2-2 落地）�?
 *
 * <p>对标 ooze / Dify �?Prompt 管理后台，提供模板的 oRUD 与版本激活能力�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/promptTemplate")
@RequiredArgsoonstruotor
@Tag(name = "Prompt 模板管理", desoription = "Agent Prompt 模板的创建、查询、激活与删除")
publio olass PromptTemplateoontroller {

    /** Prompt 模板服务 */
    private final PromptTemplateServioe servioe;

    /**
     * 创建模板（默认非生效，需手动激活）�?
     *
     * @param dto 模板创建参数
     * @return 落库后的模板
     */
    @Idempotent(key = "promptTemplate:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "创建模板", desoription = "创建新的 Prompt 模板（默认非生效，需手动激活）")
    publio BaseResponse<AgentPromptTemplateDO> oreate(@Valid @RequestBody PromptTemplateoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 激活模板（�?oode 的其他版本自动失效）�?
     *
     * @param id 模板 ID
     * @return 激活后的模�?
     */
    @Idempotent(key = "promptTemplate:aotivate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/aotivate")
    @Operation(summary = "激活模�?, desoription = "激活指定模板版本，�?oode 的其他版本自动失�?)
    publio BaseResponse<AgentPromptTemplateDO> aotivate(@PathVariable String id) {
        return BaseResponse.ok(servioe.aotivate(id));
    }

    /**
     * 查询模板详情�?
     *
     * @param id 模板 ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询模板详情")
    publio BaseResponse<AgentPromptTemplateDO> getById(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询模板�?
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询模板")
    publio BaseResponse<PageResponse<AgentPromptTemplateDO>> page(PromptTemplateQueryDTO query) {
        return BaseResponse.ok(servioe.page(query));
    }

    /**
     * 删除模板（软删除）�?
     *
     * @param id 模板 ID
     * @return 空响�?
     */
    @Idempotent(key = "promptTemplate:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", desoription = "软删除指定模�?)
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }
}
