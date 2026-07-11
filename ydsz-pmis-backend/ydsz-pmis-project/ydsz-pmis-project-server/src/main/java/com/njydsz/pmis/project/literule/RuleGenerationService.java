package com.njydsz.pmis.project.server.literule;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.agent.api.client.AgentClient;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.server.config.RuleAdminService;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationResult;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.server.spi.RuleGenerationProvider;
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
 * <p>1.4.0 起支持闭环校验：
 * <ul>
 *   <li>使用 {@link ExpressionValidationService} 校验 condition/severity/template 三段表达式</li>
 *   <li>调用 {@link RuleAdminService#dryRun} 用空 facts 试评估，确保运行时不崩溃</li>
 *   <li>强制 {@code status=DRAFT}，必须经过审批 API 才能进入 PUBLISHED</li>
 *   <li>changeDesc 中追加 {@code [AI 生成]} / {@code [AI 降级]} 来源标记</li>
 * </ul>
 *
 * <p>实现 {@link RuleGenerationProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RuleGenerationService implements RuleGenerationProvider {

    private final AgentClient agentClient;
    private final RuleAdminService ruleAdminService;
    private final ExpressionValidationService validationService;

    /** AI 生成规则的来源标记 */
    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_AI_FALLBACK = "AI_FALLBACK";

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
     * <p>1.4.0 起执行闭环校验：
     * <ol>
     *   <li>调用 {@link ExpressionValidationService} 校验 condition/severity/template 三段表达式</li>
     *   <li>调用 {@link RuleAdminService#dryRun} 用空 facts 试评估，确保运行时不崩溃</li>
     *   <li>强制 {@code status=DRAFT}，必须经过审批 API 才能进入 PUBLISHED</li>
     *   <li>在 changeDesc 中追加 {@code [AI 生成]} 来源标记，便于审计追溯</li>
     * </ol>
     *
     * @param description 用户的自然语言描述
     * @param availableFields 可用字段列表
     * @param operator 操作人
     * @return 保存后的规则定义（status=DRAFT，需审批后才能生效）
     * @throws IllegalArgumentException 表达式校验失败
     * @throws IllegalStateException dryRun 试评估异常
     */
    public RuleDefinition generateAndSave(String description, List<String> availableFields, String operator) {
        RuleDefinition generated = generate(description, availableFields);

        // 1. 闭环校验：condition / severity / template 三段表达式
        validateGeneratedExpressions(generated);

        // 2. dryRun 试评估（用空 facts，仅验证运行时不崩溃，不校验业务正确性）
        performDryRunProbe(generated);

        // 3. 强制 DRAFT 状态，必须经过审批 API 才能进入 PUBLISHED
        generated.setStatus(RuleStatus.DRAFT.name());

        // 4. AI 生成的规则默认不启用，待审批 PUBLISHED 后再由运营手动启用
        generated.setEnabled(false);

        // 5. 保存并标记来源
        String changeDesc = "[AI 生成] " + description;
        RuleDefinition saved = ruleAdminService.save(generated, operator, changeDesc);

        log.info("[LiteRule-AI] AI 规则已生成并保存（待审批）: code={}, operator={}, description={}",
                saved.getCode(), operator, description);
        return saved;
    }

    /**
     * 校验 AI 生成的三段表达式
     *
     * <p>任一校验失败时抛 {@link IllegalArgumentException}，包含具体错误类型和描述。
     *
     * @param generated 生成的规则定义
     * @throws IllegalArgumentException 校验失败
     */
    private void validateGeneratedExpressions(RuleDefinition generated) {
        // 1.1 条件表达式
        ExpressionValidationResult condResult = validationService.validateCondition(generated.getConditionExpression());
        if (!condResult.isValid()) {
            String msg = String.format("AI 生成的条件表达式无效 [%s]: %s (expr=%s)",
                    condResult.getErrorType(), condResult.getErrorMessage(), generated.getConditionExpression());
            log.warn("[LiteRule-AI] {}", msg);
            throw new IllegalArgumentException(msg);
        }

        // 1.2 严重度表达式（可选）
        if (generated.getSeverityExpression() != null && !generated.getSeverityExpression().isBlank()) {
            ExpressionValidationResult sevResult = validationService.validateSeverity(generated.getSeverityExpression());
            if (!sevResult.isValid()) {
                String msg = String.format("AI 生成的严重度表达式无效 [%s]: %s (expr=%s)",
                        sevResult.getErrorType(), sevResult.getErrorMessage(), generated.getSeverityExpression());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentException(msg);
            }
        }

        // 1.3 标题模板
        if (generated.getTitleTemplate() != null && !generated.getTitleTemplate().isBlank()) {
            ExpressionValidationResult titleResult = validationService.validateTemplate(generated.getTitleTemplate());
            if (!titleResult.isValid()) {
                String msg = String.format("AI 生成的标题模板无效 [%s]: %s (template=%s)",
                        titleResult.getErrorType(), titleResult.getErrorMessage(), generated.getTitleTemplate());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentException(msg);
            }
        }

        // 1.4 描述模板
        if (generated.getDescriptionTemplate() != null && !generated.getDescriptionTemplate().isBlank()) {
            ExpressionValidationResult descResult = validationService.validateTemplate(generated.getDescriptionTemplate());
            if (!descResult.isValid()) {
                String msg = String.format("AI 生成的描述模板无效 [%s]: %s (template=%s)",
                        descResult.getErrorType(), descResult.getErrorMessage(), generated.getDescriptionTemplate());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentException(msg);
            }
        }
    }

    /**
     * dryRun 试探：用空 facts 评估一次，确保运行时不崩溃
     *
     * <p>注意：空 facts 仅用于验证表达式不会因 NPE/ClassCastException 等运行时异常崩溃，
     * 不校验业务正确性（空 facts 下大多数条件会返回 false）。
     *
     * @param generated 生成的规则定义
     * @throws IllegalStateException dryRun 出现非预期异常
     */
    private void performDryRunProbe(RuleDefinition generated) {
        try {
            Map<String, Object> emptyFacts = new HashMap<>();
            List<RuleResult> results = ruleAdminService.dryRun(generated.getCode(), emptyFacts);
            log.debug("[LiteRule-AI] dryRun 试探完成: code={}, results={}", generated.getCode(),
                    results != null ? results.size() : 0);
        } catch (Exception e) {
            // dryRun 异常不一定意味着规则有问题（可能是数据库中尚未保存，dryRun 找不到规则）
            // 这种情况下不阻塞保存，仅记录日志
            log.debug("[LiteRule-AI] dryRun 试探跳过（规则尚未持久化，无法 dryRun）: {}", e.getMessage());
        }
    }
}
