package com.njydsz.pmis.execution.literule;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.common.feign.AgentClient;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.config.RuleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助规则生成服务
 *
 * <p>基于用户的自然语言描述，调用 LLM（通过 agent 模块）生成 Aviator 表达式规则。
 * 当 AI 调用失败时，降级为基于关键词的简单规则生成策略。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleGenerationService {

    private final AgentClient agentClient;
    private final RuleAdminService ruleAdminService;

    /**
     * AI 辅助生成规则定义
     *
     * <p>基于用户的自然语言描述，调用 LLM 生成 Aviator 表达式规则。
     *
     * @param description 用户的自然语言描述（如"当 CPI 低于 0.85 且项目预算超 50万时红色预警"）
     * @param availableFields 可用字段列表（如 ["cpi", "budgetAmount", "evmRedCount"]）
     * @return 生成的规则定义（未保存，仅建议）
     */
    public RuleDefinition generate(String description, List<String> availableFields) {
        // 构造 prompt
        String prompt = buildPrompt(description, availableFields);

        // 调用 agent 模块
        Map<String, Object> body = new HashMap<>();
        body.put("agentType", "RULE_GENERATION");
        body.put("bizType", "RULE_DEFINITION");
        body.put("bizId", 0L);
        body.put("bizRef", "literule-gen");
        Map<String, Object> params = new HashMap<>();
        params.put("prompt", prompt);
        params.put("description", description);
        params.put("availableFields", availableFields);
        body.put("params", params);

        try {
            var result = agentClient.execute(body);
            if (result != null && result.getData() != null) {
                return parseGenerationResult(result.getData(), description);
            }
        } catch (Exception e) {
            log.warn("[LiteRule-AI] AI 规则生成失败，使用降级策略: {}", e.getMessage());
        }

        // 降级：基于关键词的简单规则生成
        return fallbackGenerate(description, availableFields);
    }

    /**
     * 构造 LLM prompt
     */
    private String buildPrompt(String description, List<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个规则引擎专家。请根据以下描述生成一个 Aviator 表达式规则。\n\n");
        sb.append("规则描述：").append(description).append("\n\n");
        sb.append("可用字段：").append(String.join(", ", fields)).append("\n\n");
        sb.append("请返回 JSON 格式的规则定义，包含以下字段：\n");
        sb.append("{\n");
        sb.append("  \"code\": \"规则编码（大写下划线）\",\n");
        sb.append("  \"name\": \"规则名称（中文）\",\n");
        sb.append("  \"category\": \"类别（EVM/COST/BENCH/UTILIZATION/BUDGET/SLA/GENERAL）\",\n");
        sb.append("  \"conditionExpression\": \"Aviator 条件表达式\",\n");
        sb.append("  \"severityExpression\": \"严重度表达式（可选）\",\n");
        sb.append("  \"defaultSeverity\": \"YELLOW 或 RED\",\n");
        sb.append("  \"titleTemplate\": \"标题模板（支持 ${var}）\",\n");
        sb.append("  \"descriptionTemplate\": \"描述模板\",\n");
        sb.append("  \"priority\": 100\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 解析 AI 生成结果
     */
    @SuppressWarnings("unchecked")
    private RuleDefinition parseGenerationResult(Map<String, Object> data, String description) {
        try {
            // agent 返回的 payload 中包含规则 JSON
            Object payload = data.get("payload");
            if (payload instanceof String strPayload) {
                JSONObject json = JSON.parseObject(strPayload);
                return jsonToRuleDefinition(json);
            } else if (payload instanceof Map mapPayload) {
                return jsonToRuleDefinition(new JSONObject(mapPayload));
            }
            // 如果 data 本身就是规则定义
            return jsonToRuleDefinition(new JSONObject(data));
        } catch (Exception e) {
            log.warn("[LiteRule-AI] AI 结果解析失败: {}", e.getMessage());
            return fallbackGenerate(description, List.of());
        }
    }

    /**
     * JSON → RuleDefinition
     */
    private RuleDefinition jsonToRuleDefinition(JSONObject json) {
        return RuleDefinition.builder()
                .code(json.getString("code"))
                .name(json.getString("name"))
                .category(json.getString("category"))
                .conditionExpression(json.getString("conditionExpression"))
                .severityExpression(json.getString("severityExpression"))
                .defaultSeverity(RuleSeverity.fromCode(json.getString("defaultSeverity")))
                .titleTemplate(json.getString("titleTemplate"))
                .descriptionTemplate(json.getString("descriptionTemplate"))
                .priority(json.getIntValue("priority", 100))
                .enabled(false) // AI 生成的规则默认不启用
                .build();
    }

    /**
     * 降级策略：基于关键词的简单规则生成
     */
    private RuleDefinition fallbackGenerate(String description, List<String> fields) {
        // 根据关键词匹配常见规则模式
        String lowerDesc = description.toLowerCase();

        if (lowerDesc.contains("cpi") || lowerDesc.contains("成本绩效")) {
            return RuleDefinition.builder()
                    .code("AI_GEN_CPI")
                    .name("AI 生成 - CPI 预警")
                    .category("EVM")
                    .conditionExpression("cpi < 0.85")
                    .severityExpression("cpi < 0.70 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("CPI ${cpi} 偏低")
                    .descriptionTemplate("CPI 为 ${cpi}，低于阈值 0.85")
                    .priority(100)
                    .enabled(false)
                    .build();
        }

        if (lowerDesc.contains("毛利") || lowerDesc.contains("margin")) {
            return RuleDefinition.builder()
                    .code("AI_GEN_MARGIN")
                    .name("AI 生成 - 毛利率预警")
                    .category("COST")
                    .conditionExpression("grossMargin < 0.10 && confirmedRevenue > 0")
                    .severityExpression("grossMargin < 0.05 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("毛利率 ${grossMargin} 偏低")
                    .descriptionTemplate("毛利率 ${grossMargin} 低于阈值")
                    .priority(110)
                    .enabled(false)
                    .build();
        }

        if (lowerDesc.contains("预算") || lowerDesc.contains("budget")) {
            return RuleDefinition.builder()
                    .code("AI_GEN_BUDGET")
                    .name("AI 生成 - 预算预警")
                    .category("BUDGET")
                    .conditionExpression("budgetUsageRatio >= 0.80")
                    .severityExpression("budgetUsageRatio >= 0.95 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("预算使用率 ${budgetUsageRatio}")
                    .descriptionTemplate("预算使用率 ${budgetUsageRatio}，接近或超出预算")
                    .priority(105)
                    .enabled(false)
                    .build();
        }

        // 默认模板
        return RuleDefinition.builder()
                .code("AI_GEN_CUSTOM")
                .name("AI 生成 - 自定义规则")
                .category("GENERAL")
                .conditionExpression("true")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate(description)
                .descriptionTemplate("AI 生成的自定义规则：" + description)
                .priority(100)
                .enabled(false)
                .build();
    }

    /**
     * 生成并保存规则
     *
     * @param description 用户的自然语言描述
     * @param availableFields 可用字段列表
     * @param operator 操作人
     * @return 保存后的规则定义
     */
    public RuleDefinition generateAndSave(String description, List<String> availableFields, String operator) {
        RuleDefinition generated = generate(description, availableFields);
        // 校验表达式
        if (!ruleAdminService.validateExpression(generated.getConditionExpression())) {
            log.warn("[LiteRule-AI] AI 生成的表达式无效: {}", generated.getConditionExpression());
            throw new IllegalArgumentException("AI 生成的表达式无效: " + generated.getConditionExpression());
        }
        return ruleAdminService.save(generated, operator, "AI 辅助生成: " + description);
    }
}
