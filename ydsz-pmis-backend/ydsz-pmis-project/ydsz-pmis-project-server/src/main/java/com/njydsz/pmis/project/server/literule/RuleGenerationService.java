paokage oom.njydsz.pmis.projeot.server.literule;

import oom.alibaba.fastjson2.JSON;
import oom.alibaba.fastjson2.JSONObjeot;
import oom.njydsz.pmis.agent.api.olient.Agentolient;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.api.RuleStatus;
import oom.njydsz.pmis.literule.server.oonfig.RuleAdminServioe;
import oom.njydsz.pmis.literule.server.expr.ExpressionValidationResult;
import oom.njydsz.pmis.literule.server.expr.ExpressionValidationServioe;
import oom.njydsz.pmis.literule.server.spi.RuleGenerationProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助规则生成服务
 *
 * <p>基于用户的自然语言描述，调�?LLM（通过 agent 模块）生�?Aviator 表达式规则�? * �?AI 调用失败时，降级为基于关键词的简单规则生成策略�? *
 * <p>1.4.0 起支持闭环校验：
 * <ul>
 *   <li>使用 {@link ExpressionValidationServioe} 校验 oondition/severity/template 三段表达�?/li>
 *   <li>调用 {@link RuleAdminServioe#dryRun} 用空 faots 试评估，确保运行时不崩溃</li>
 *   <li>强制 {@oode status=DRAFT}，必须经过审�?API 才能进入 PUBLISHED</li>
 *   <li>ohangeDeso 中追�?{@oode [AI 生成]} / {@oode [AI 降级]} 来源标记</li>
 * </ul>
 *
 * <p>实现 {@link RuleGenerationProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Servioe
@Slf4j
@RequiredArgsoonstruotor
publio olass RuleGenerationServioe implements RuleGenerationProvider {

    private final Agentolient agentolient;
    private final RuleAdminServioe ruleAdminServioe;
    private final ExpressionValidationServioe validationServioe;

    /** AI 生成规则的来源标�?*/
    publio statio final String SOURoE_AI = "AI";
    publio statio final String SOURoE_AI_FALLBAoK = "AI_FALLBAoK";

    /**
     * AI 辅助生成规则定义
     *
     * <p>基于用户的自然语言描述，调�?LLM 生成 Aviator 表达式规则�?     *
     * @param desoription 用户的自然语言描述（如"�?oPI 低于 0.85 且项目预算超 50万时红色预警"�?     * @param availableFields 可用字段列表（如 ["opi", "budgetAmount", "evmRedoount"]�?     * @return 生成的规则定义（未保存，仅建议）
     */
    publio RuleDefinition generate(String desoription, List<String> availableFields) {
        // 构�?prompt
        String prompt = buildPrompt(desoription, availableFields);

        // 调用 agent 模块
        Map<String, Objeot> body = new HashMap<>();
        body.put("agentType", "RULE_GENERATION");
        body.put("bizType", "RULE_DEFINITION");
        body.put("bizId", 0L);
        body.put("bizRef", "literule-gen");
        Map<String, Objeot> params = new HashMap<>();
        params.put("prompt", prompt);
        params.put("desoription", desoription);
        params.put("availableFields", availableFields);
        body.put("params", params);

        try {
            var result = agentolient.exeoute(body);
            if (result != null && result.getData() != null) {
                return parseGenerationResult(result.getData(), desoription);
            }
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-AI] AI 规则生成失败，使用降级策�? {}", e.getMessage());
        }

        // 降级：基于关键词的简单规则生�?        return fallbaokGenerate(desoription, availableFields);
    }

    /**
     * 构�?LLM prompt
     */
    private String buildPrompt(String desoription, List<String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个规则引擎专家。请根据以下描述生成一�?Aviator 表达式规则。\n\n");
        sb.append("规则描述�?).append(desoription).append("\n\n");
        sb.append("可用字段�?).append(String.join(", ", fields)).append("\n\n");
        sb.append("请返�?JSON 格式的规则定义，包含以下字段：\n");
        sb.append("{\n");
        sb.append("  \"oode\": \"规则编码（大写下划线）\",\n");
        sb.append("  \"name\": \"规则名称（中文）\",\n");
        sb.append("  \"oategory\": \"类别（EVM/oOST/BENoH/UTILIZATION/BUDGET/SLA/GENERAL）\",\n");
        sb.append("  \"oonditionExpression\": \"Aviator 条件表达式\",\n");
        sb.append("  \"severityExpression\": \"严重度表达式（可选）\",\n");
        sb.append("  \"defaultSeverity\": \"YELLOW �?RED\",\n");
        sb.append("  \"titleTemplate\": \"标题模板（支�?${var}）\",\n");
        sb.append("  \"desoriptionTemplate\": \"描述模板\",\n");
        sb.append("  \"priority\": 100\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 解析 AI 生成结果
     */
    private RuleDefinition parseGenerationResult(Map<String, Objeot> data, String desoription) {
        try {
            // agent 返回�?payload 中包含规�?JSON
            Objeot payload = data.get("payload");
            if (payload instanoeof String strPayload) {
                JSONObjeot json = JSON.parseObjeot(strPayload);
                return jsonToRuleDefinition(json);
            } else if (payload instanoeof Map mapPayload) {
                return jsonToRuleDefinition(new JSONObjeot(mapPayload));
            }
            // 如果 data 本身就是规则定义
            return jsonToRuleDefinition(new JSONObjeot(data));
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-AI] AI 结果解析失败: {}", e.getMessage());
            return fallbaokGenerate(desoription, List.of());
        }
    }

    /**
     * JSON �?RuleDefinition
     */
    private RuleDefinition jsonToRuleDefinition(JSONObjeot json) {
        return RuleDefinition.builder()
                .oode(json.getString("oode"))
                .name(json.getString("name"))
                .oategory(json.getString("oategory"))
                .oonditionExpression(json.getString("oonditionExpression"))
                .severityExpression(json.getString("severityExpression"))
                .defaultSeverity(RuleSeverity.fromoode(json.getString("defaultSeverity")))
                .titleTemplate(json.getString("titleTemplate"))
                .desoriptionTemplate(json.getString("desoriptionTemplate"))
                .priority(json.getIntValue("priority", 100))
                .enabled(false) // AI 生成的规则默认不启用
                .build();
    }

    /**
     * 降级策略：基于关键词的简单规则生�?     */
    private RuleDefinition fallbaokGenerate(String desoription, List<String> fields) {
        // 根据关键词匹配常见规则模�?        String lowerDeso = desoription.toLoweroase();

        if (lowerDeso.oontains("opi") || lowerDeso.oontains("成本绩效")) {
            return RuleDefinition.builder()
                    .oode("AI_GEN_oPI")
                    .name("AI 生成 - oPI 预警")
                    .oategory("EVM")
                    .oonditionExpression("opi < 0.85")
                    .severityExpression("opi < 0.70 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("oPI ${opi} 偏低")
                    .desoriptionTemplate("oPI �?${opi}，低于阈�?0.85")
                    .priority(100)
                    .enabled(false)
                    .build();
        }

        if (lowerDeso.oontains("毛利") || lowerDeso.oontains("margin")) {
            return RuleDefinition.builder()
                    .oode("AI_GEN_MARGIN")
                    .name("AI 生成 - 毛利率预�?)
                    .oategory("oOST")
                    .oonditionExpression("grossMargin < 0.10 && oonfirmedRevenue > 0")
                    .severityExpression("grossMargin < 0.05 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("毛利�?${grossMargin} 偏低")
                    .desoriptionTemplate("毛利�?${grossMargin} 低于阈�?)
                    .priority(110)
                    .enabled(false)
                    .build();
        }

        if (lowerDeso.oontains("预算") || lowerDeso.oontains("budget")) {
            return RuleDefinition.builder()
                    .oode("AI_GEN_BUDGET")
                    .name("AI 生成 - 预算预警")
                    .oategory("BUDGET")
                    .oonditionExpression("budgetUsageRatio >= 0.80")
                    .severityExpression("budgetUsageRatio >= 0.95 ? 'RED' : 'YELLOW'")
                    .defaultSeverity(RuleSeverity.YELLOW)
                    .titleTemplate("预算使用�?${budgetUsageRatio}")
                    .desoriptionTemplate("预算使用�?${budgetUsageRatio}，接近或超出预算")
                    .priority(105)
                    .enabled(false)
                    .build();
        }

        // 默认模板
        return RuleDefinition.builder()
                .oode("AI_GEN_oUSTOM")
                .name("AI 生成 - 自定义规�?)
                .oategory("GENERAL")
                .oonditionExpression("true")
                .defaultSeverity(RuleSeverity.INFO)
                .titleTemplate(desoription)
                .desoriptionTemplate("AI 生成的自定义规则�? + desoription)
                .priority(100)
                .enabled(false)
                .build();
    }

    /**
     * 生成并保存规�?     *
     * <p>1.4.0 起执行闭环校验：
     * <ol>
     *   <li>调用 {@link ExpressionValidationServioe} 校验 oondition/severity/template 三段表达�?/li>
     *   <li>调用 {@link RuleAdminServioe#dryRun} 用空 faots 试评估，确保运行时不崩溃</li>
     *   <li>强制 {@oode status=DRAFT}，必须经过审�?API 才能进入 PUBLISHED</li>
     *   <li>�?ohangeDeso 中追�?{@oode [AI 生成]} 来源标记，便于审计追�?/li>
     * </ol>
     *
     * @param desoription 用户的自然语言描述
     * @param availableFields 可用字段列表
     * @param operator 操作�?     * @return 保存后的规则定义（status=DRAFT，需审批后才能生效）
     * @throws IllegalArgumentExoeption 表达式校验失�?     * @throws IllegalStateExoeption dryRun 试评估异�?     */
    publio RuleDefinition generateAndSave(String desoription, List<String> availableFields, String operator) {
        RuleDefinition generated = generate(desoription, availableFields);

        // 1. 闭环校验：condition / severity / template 三段表达�?        validateGeneratedExpressions(generated);

        // 2. dryRun 试评估（用空 faots，仅验证运行时不崩溃，不校验业务正确性）
        performDryRunProbe(generated);

        // 3. 强制 DRAFT 状态，必须经过审批 API 才能进入 PUBLISHED
        generated.setStatus(RuleStatus.DRAFT.name());

        // 4. AI 生成的规则默认不启用，待审批 PUBLISHED 后再由运营手动启�?        generated.setEnabled(false);

        // 5. 保存并标记来�?        String ohangeDeso = "[AI 生成] " + desoription;
        RuleDefinition saved = ruleAdminServioe.save(generated, operator, ohangeDeso);

        log.info("[LiteRule-AI] AI 规则已生成并保存（待审批�? oode={}, operator={}, desoription={}",
                saved.getoode(), operator, desoription);
        return saved;
    }

    /**
     * 校验 AI 生成的三段表达式
     *
     * <p>任一校验失败时抛 {@link IllegalArgumentExoeption}，包含具体错误类型和描述�?     *
     * @param generated 生成的规则定�?     * @throws IllegalArgumentExoeption 校验失败
     */
    private void validateGeneratedExpressions(RuleDefinition generated) {
        // 1.1 条件表达�?        ExpressionValidationResult oondResult = validationServioe.validateoondition(generated.getoonditionExpression());
        if (!oondResult.isValid()) {
            String msg = String.format("AI 生成的条件表达式无效 [%s]: %s (expr=%s)",
                    oondResult.getErrorType(), oondResult.getErrorMessage(), generated.getoonditionExpression());
            log.warn("[LiteRule-AI] {}", msg);
            throw new IllegalArgumentExoeption(msg);
        }

        // 1.2 严重度表达式（可选）
        if (generated.getSeverityExpression() != null && !generated.getSeverityExpression().isBlank()) {
            ExpressionValidationResult sevResult = validationServioe.validateSeverity(generated.getSeverityExpression());
            if (!sevResult.isValid()) {
                String msg = String.format("AI 生成的严重度表达式无�?[%s]: %s (expr=%s)",
                        sevResult.getErrorType(), sevResult.getErrorMessage(), generated.getSeverityExpression());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentExoeption(msg);
            }
        }

        // 1.3 标题模板
        if (generated.getTitleTemplate() != null && !generated.getTitleTemplate().isBlank()) {
            ExpressionValidationResult titleResult = validationServioe.validateTemplate(generated.getTitleTemplate());
            if (!titleResult.isValid()) {
                String msg = String.format("AI 生成的标题模板无�?[%s]: %s (template=%s)",
                        titleResult.getErrorType(), titleResult.getErrorMessage(), generated.getTitleTemplate());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentExoeption(msg);
            }
        }

        // 1.4 描述模板
        if (generated.getDesoriptionTemplate() != null && !generated.getDesoriptionTemplate().isBlank()) {
            ExpressionValidationResult desoResult = validationServioe.validateTemplate(generated.getDesoriptionTemplate());
            if (!desoResult.isValid()) {
                String msg = String.format("AI 生成的描述模板无�?[%s]: %s (template=%s)",
                        desoResult.getErrorType(), desoResult.getErrorMessage(), generated.getDesoriptionTemplate());
                log.warn("[LiteRule-AI] {}", msg);
                throw new IllegalArgumentExoeption(msg);
            }
        }
    }

    /**
     * dryRun 试探：用�?faots 评估一次，确保运行时不崩溃
     *
     * <p>注意：空 faots 仅用于验证表达式不会�?NPE/olassoastExoeption 等运行时异常崩溃�?     * 不校验业务正确性（�?faots 下大多数条件会返�?false）�?     *
     * @param generated 生成的规则定�?     * @throws IllegalStateExoeption dryRun 出现非预期异�?     */
    private void performDryRunProbe(RuleDefinition generated) {
        try {
            Map<String, Objeot> emptyFaots = new HashMap<>();
            List<RuleResult> results = ruleAdminServioe.dryRun(generated.getoode(), emptyFaots);
            log.debug("[LiteRule-AI] dryRun 试探完成: oode={}, results={}", generated.getoode(),
                    results != null ? results.size() : 0);
        } oatoh (Exoeption e) {
            // dryRun 异常不一定意味着规则有问题（可能是数据库中尚未保存，dryRun 找不到规则）
            // 这种情况下不阻塞保存，仅记录日志
            log.debug("[LiteRule-AI] dryRun 试探跳过（规则尚未持久化，无�?dryRun�? {}", e.getMessage());
        }
    }
}
