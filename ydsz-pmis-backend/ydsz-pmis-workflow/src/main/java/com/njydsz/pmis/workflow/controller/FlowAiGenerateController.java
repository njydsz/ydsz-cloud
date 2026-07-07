package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.service.FlowAiGenerateService;
import com.njydsz.pmis.workflow.dto.FlowAiGenerateDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P0-3: AI 一句话生成流程 HTTP API
 *
 * <p>接收自然语言流程描述，调用 AI Agent 生成 BPMN 2.0 XML 流程定义。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-ai-generate", description = "工作流AI生成接口")
@RequestMapping("/workflow/ai")
@RequiredArgsConstructor
@Validated
public class FlowAiGenerateController {

    private final FlowAiGenerateService flowAiGenerateService;

    /**
     * AI 一句话生成流程
     *
     * <p>请求体：{ "description": "请假审批：直属领导审批 → 部门经理审批（3天以上）→ 人事备案" }
     * 响应体：{ "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" }
     *
     * @param body 请求体，需包含 description 字段
     * @return 包含 bpmnXml 字段的响应数据
     */
    @PostMapping("/generate")
    @Operation(summary = "AI一句话生成流程")
    public Result<Map<String, Object>> generate(@Valid @RequestBody FlowAiGenerateDTO dto) {
        String description = dto.getDescription();
        log.info("[FlowAiGenerate] 收到生成请求, description.length={}", description.length());

        String bpmnXml = flowAiGenerateService.generateBpmnFromDescription(description);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bpmnXml", bpmnXml);
        return Result.ok(data);
    }
}
