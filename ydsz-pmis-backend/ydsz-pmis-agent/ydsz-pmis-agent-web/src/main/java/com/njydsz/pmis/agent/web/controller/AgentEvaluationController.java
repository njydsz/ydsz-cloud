package com.njydsz.pmis.agent.web.controller.agent;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.server.engine.Agent;
import com.njydsz.pmis.agent.server.engine.AgentContext;
import com.njydsz.pmis.agent.server.engine.AgentResult;
import com.njydsz.pmis.agent.server.engine.eval.AgentEvaluationFramework;
import com.njydsz.pmis.agent.server.engine.eval.EvaluationCase;
import com.njydsz.pmis.agent.server.engine.eval.EvaluationReport;
import com.njydsz.pmis.agent.server.engine.eval.EvaluableAgent;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 评测框架 Controller。
 *
 * <p>提供评测用例的执行与报告查询接口，供前端评测管理页面使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "AI 智能体 - 评测框架")
@RestController
@RequestMapping("/agent/evaluation")
@RequiredArgsConstructor
public class AgentEvaluationController {

    /** Agent 实例 Map（key = agentType, value = Agent 实例） */
    private final Map<String, Agent> agentMap;

    /**
     * 执行评测。
     *
     * <p>接收评测用例列表，对指定 Agent 执行批量评测，返回评测报告。
     *
     * @param req 评测请求
     * @return 评测报告
     */
    @Operation(summary = "执行评测")
    @AuthApiPermission(apiCodes = "agent:task:run")
    @Idempotent(key = "agentEvaluation:runEvaluation", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/run")
    public BaseResponse<EvaluationReport> runEvaluation(@RequestBody EvaluationRunRequest req) {
        if (req.getAgentType() == null || req.getAgentType().isBlank()) {
            return BaseResponse.fail("agentType 不能为空");
        }
        Agent agent = agentMap.get(req.getAgentType());
        if (agent == null) {
            return BaseResponse.fail("Agent 类型不存在: " + req.getAgentType());
        }

        List<EvaluationCase> cases = new ArrayList<>();
        for (EvaluationCaseDTO dto : req.getCases()) {
            cases.add(EvaluationCase.builder()
                    .id(dto.getId())
                    .userInput(dto.getUserInput())
                    .expectedOutput(dto.getExpectedOutput())
                    .evaluator(EvaluationCase.EvaluatorType.valueOf(dto.getEvaluator()))
                    .passThreshold(dto.getPassThreshold() > 0 ? dto.getPassThreshold() : 0.6)
                    .tag(dto.getTag())
                    .build());
        }

        try (AgentEvaluationFramework framework = new AgentEvaluationFramework(
                toEvaluable(agent), null, req.getParallelism() > 0 ? req.getParallelism() : 1)) {
            EvaluationReport report = framework.run(cases);
            return BaseResponse.ok(report);
        }
    }

    /**
     * 将 {@link Agent}（{@code execute(AgentContext)}）适配为 {@link EvaluableAgent}（{@code execute(String, AgentContext)}）。
     *
     * <p>用户输入通过 {@code AgentContext.params["userInput"]} 传入，由具体 Agent 自行解释；
     * 输出取 {@link AgentResult#getSuggestion()}，为空时回退到 {@code toString()}。
     */
    private EvaluableAgent toEvaluable(Agent agent) {
        return (input, ctx) -> {
            AgentContext context = ctx != null ? ctx : new AgentContext();
            Map<String, Object> params = context.getParams();
            if (params == null) {
                params = new HashMap<>();
                context.setParams(params);
            }
            params.put("userInput", input);
            AgentResult result = agent.execute(context);
            return BaseResponse.getSuggestion() != null ? BaseResponse.getSuggestion() : BaseResponse.toString();
        };
    }

    /**
     * 获取评估器类型列表。
     *
     * @return 评估器类型枚举列表
     */
    @Operation(summary = "评估器类型列表")
    @AuthApiPermission(apiCodes = "agent:task:list")
    @GetMapping("/evaluators")
    public BaseResponse<List<Map<String, String>>> listEvaluators() {
        List<Map<String, String>> evaluators = new ArrayList<>();
        for (EvaluationCase.EvaluatorType type : EvaluationCase.EvaluatorType.values()) {
            evaluators.add(Map.of(
                    "code", type.name(),
                    "desc", getEvaluatorDesc(type)
            ));
        }
        return BaseResponse.ok(evaluators);
    }

    /**
     * 获取评估器类型的中文描述。
     *
     * @param type 评估器类型枚举
     * @return 中文描述
     */
    private String getEvaluatorDesc(EvaluationCase.EvaluatorType type) {
        return switch (type) {
            case EXACT_MATCH -> "精确匹配";
            case KEYWORD_CONTAINS -> "关键词包含";
            case COSINE_SIMILARITY -> "余弦相似度";
            case LLM_AS_JUDGE -> "LLM 评审";
            case CUSTOM -> "自定义";
        };
    }

    /** 评测执行请求 */
    @Data
    public static class EvaluationRunRequest {
        /** 被评测的 Agent 类型 */
        private String agentType;
        /** 评测用例列表 */
        private List<EvaluationCaseDTO> cases;
        /** 并行度（1=串行） */
        private int parallelism;
    }

    /** 评测用例 DTO */
    @Data
    public static class EvaluationCaseDTO {
        /** 用例 ID */
        private String id;
        /** 用户输入 */
        private String userInput;
        /** 期望输出 */
        private String expectedOutput;
        /** 评估器类型 */
        private String evaluator;
        /** 通过阈值 */
        private double passThreshold;
        /** 标签 */
        private String tag;
    }
}
