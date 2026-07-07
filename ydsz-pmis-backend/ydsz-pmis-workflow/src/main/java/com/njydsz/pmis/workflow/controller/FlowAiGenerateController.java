package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.service.FlowAiAssistService;
import com.njydsz.pmis.workflow.service.FlowAiGenerateService;
import com.njydsz.pmis.workflow.dto.FlowAiGenerateDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
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
 * <p>P3-1: 扩展 3 个 AI 分析端点：
 * <ul>
 *   <li>{@code POST /workflow/ai/predict-risk} — 流程风险预测</li>
 *   <li>{@code POST /workflow/ai/smart-remind} — 智能催办</li>
 *   <li>{@code POST /workflow/ai/predict-sla} — SLA 预测</li>
 * </ul>
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
    private final FlowAiAssistService flowAiAssistService;

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

    // ============================== P3-1: AI 能力扩展 ==============================

    /**
     * P3-1: 流程风险预测。
     *
     * <p>请求体：{ "instanceId": "...", "flowCode": "...", "businessTitle": "...", "amount": 10000 }
     * 响应体：{ "riskLevel": "HIGH", "rejectProbability": 0.7, "overdueProbability": 0.3, "reasons": [...] }
     *
     * @param body 请求体，需包含 instanceId 或 flowCode
     * @return 风险预测结果
     */
    @PostMapping("/predict-risk")
    @Operation(summary = "P3-1: 流程风险预测")
    public Result<Map<String, Object>> predictRisk(@RequestBody Map<String, Object> body) {
        validateRiskParams(body);
        log.info("[FlowAi] 风险预测请求: instanceId={} flowCode={}",
                body.get("instanceId"), body.get("flowCode"));
        return Result.ok(flowAiAssistService.predictRisk(body));
    }

    /**
     * P3-1: 智能催办。
     *
     * <p>请求体：{ "taskId": "...", "assigneeId": "...", "flowCode": "...", "nodeCode": "..." }
     * 响应体：{ "bestTime": "IMMEDIATE", "channel": "IN_APP", "message": "...", "reasons": [...] }
     *
     * @param body 请求体，需包含 taskId 与 assigneeId
     * @return 智能催办建议
     */
    @PostMapping("/smart-remind")
    @Operation(summary = "P3-1: 智能催办")
    public Result<Map<String, Object>> smartRemind(@RequestBody Map<String, Object> body) {
        validateRemindParams(body);
        log.info("[FlowAi] 智能催办请求: taskId={} assigneeId={}",
                body.get("taskId"), body.get("assigneeId"));
        return Result.ok(flowAiAssistService.smartRemind(body));
    }

    /**
     * P3-1: SLA 预测。
     *
     * <p>请求体：{ "instanceId": "...", "flowCode": "...", "currentNodeCode": "..." }
     * 响应体：{ "estimatedDurationMs": 3600000, "estimatedCompleteAt": "2026-07-08T18:00:00", "confidence": 0.85, "reasons": [...] }
     *
     * @param body 请求体，需包含 instanceId 或 flowCode
     * @return SLA 预测结果
     */
    @PostMapping("/predict-sla")
    @Operation(summary = "P3-1: SLA 预测")
    public Result<Map<String, Object>> predictSla(@RequestBody Map<String, Object> body) {
        validateSlaParams(body);
        log.info("[FlowAi] SLA 预测请求: instanceId={} flowCode={}",
                body.get("instanceId"), body.get("flowCode"));
        return Result.ok(flowAiAssistService.predictSla(body));
    }

    // ============================== 参数校验 ==============================

    private void validateRiskParams(Map<String, Object> body) {
        if (body == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_c8d9e0f1");
        }
        if (!StringUtils.hasText(String.valueOf(body.getOrDefault("instanceId", "")))
                && !StringUtils.hasText(String.valueOf(body.getOrDefault("flowCode", "")))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_c8d9e0f1");
        }
    }

    private void validateRemindParams(Map<String, Object> body) {
        if (body == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_d9e0f1a2");
        }
        if (!StringUtils.hasText(String.valueOf(body.getOrDefault("taskId", "")))
                || !StringUtils.hasText(String.valueOf(body.getOrDefault("assigneeId", "")))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_d9e0f1a2");
        }
    }

    private void validateSlaParams(Map<String, Object> body) {
        if (body == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_c8d9e0f1");
        }
        if (!StringUtils.hasText(String.valueOf(body.getOrDefault("instanceId", "")))
                && !StringUtils.hasText(String.valueOf(body.getOrDefault("flowCode", "")))) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_c8d9e0f1");
        }
    }
}
