package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.ExpressionValidationResult;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 规则 LLM 服务（P2-15 AI 增强）
 *
 * <p>封装规则相关的 LLM 能力，对上层提供三个核心方法：
 * <ul>
 *   <li>{@link #naturalLanguageToRule} - 自然语言描述转规则定义</li>
 *   <li>{@link #describeRule} - 为已有规则生成可读描述</li>
 *   <li>{@link #optimizeExpression} - 表达式优化建议</li>
 * </ul>
 *
 * <p>所有方法都做了异常隔离：LLM 不可用时降级为本地规则模板或抛
 * {@link LLMException}，业务层可以选择性捕获。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public class RuleLLMService {

    private static final Logger log = LoggerFactory.getLogger(RuleLLMService.class);

    /** 自然语言转规则的系统提示词 */
    private static final String NL2RULE_SYSTEM_PROMPT = "你是一个规则引擎专家。根据用户的自然语言描述，输出严格的 JSON 格式规则定义，"
            + "字段包括：code（kebab-case 英文编码）、name（中文名称）、conditionExpression（Aviator 语法表达式）、"
            + "defaultSeverity（RED/YELLOW/GREEN/BLUE 之一）、description（中文描述）。"
            + "不要输出任何额外解释，只输出一个 JSON 对象。";

    /** 规则描述生成的系统提示词 */
    private static final String DESCRIBE_SYSTEM_PROMPT = "你是规则引擎专家。请基于给定的规则定义，输出一段 1~3 句的中文业务描述，"
            + "说明该规则在什么场景下触发、严重度如何。语气简洁专业，不要带任何前后缀。";

    /** 表达式优化的系统提示词 */
    private static final String OPTIMIZE_SYSTEM_PROMPT = "你是 Aviator 表达式专家。请分析给定的表达式并给出 1~3 条优化建议，"
            + "建议关注：可读性、性能、可测试性、潜在边界条件。每条建议一行，不要带编号和前后缀。";

    /** LLM 客户端，用于自然语言与规则定义之间的相互转换 */
    private final LLMClient llmClient;
    /** 表达式校验服务，用于校验 LLM 生成的表达式语法合法性 */
    private final ExpressionValidationService expressionValidator;

    public RuleLLMService(LLMClient llmClient, ExpressionValidationService expressionValidator) {
        this.llmClient = llmClient;
        this.expressionValidator = expressionValidator;
    }

    /**
     * 自然语言转规则定义
     *
     * @param naturalLanguage 用户输入的自然语言描述
     * @return 解析后的 {@link RuleDefinition}（已通过表达式语法校验）；
     *         LLM 输出格式不合法时回退到只填 code/name/description 的"空壳"定义
     * @throws LLMException 当 LLM 不可用且无法回退时抛出
     */
    public RuleDefinition naturalLanguageToRule(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("自然语言描述不能为空");
        }
        String raw;
        try {
            raw = llmClient.chat(NL2RULE_SYSTEM_PROMPT, naturalLanguage, null);
        } catch (LLMException e) {
            log.warn("[LLM] naturalLanguageToRule 调用失败，降级返回空壳规则: {}", e.getMessage());
            RuleDefinition fallback = new RuleDefinition();
            fallback.setName(naturalLanguage);
            fallback.setDescription("LLM 不可用，请手动填写规则条件。");
            return fallback;
        }
        return parseRuleJson(raw, naturalLanguage);
    }

    /**
     * 为已有规则生成业务描述
     *
     * @param rule 已有的规则定义
     * @return 1~3 句中文描述；LLM 不可用时返回 {@code null}（不抛异常）
     */
    public String describeRule(RuleDefinition rule) {
        if (rule == null) {
            return null;
        }
        String userPrompt = buildRulePrompt(rule);
        try {
            return llmClient.chat(DESCRIBE_SYSTEM_PROMPT, userPrompt, null).trim();
        } catch (LLMException e) {
            log.warn("[LLM] describeRule 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 表达式优化建议
     *
     * @param expression 待优化的 Aviator 表达式
     * @return 优化建议文本（可能包含多行）；LLM 不可用时返回 {@code null}
     */
    public String optimizeExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        try {
            return llmClient.chat(OPTIMIZE_SYSTEM_PROMPT, expression, null).trim();
        } catch (LLMException e) {
            log.warn("[LLM] optimizeExpression 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 LLM 输出的 JSON 规则
     */
    private RuleDefinition parseRuleJson(String raw, String fallbackDesc) {
        RuleDefinition.RuleDefinitionBuilder builder = RuleDefinition.builder();
        builder.description(fallbackDesc);

        if (raw == null || raw.isEmpty()) {
            return builder.build();
        }
        // 提取 JSON 片段（有些 LLM 会包裹 ```json ... ```）
        String json = extractJsonBlock(raw);
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            if (obj == null) {
                return builder.build();
            }
            String code = obj.getString("code");
            String name = obj.getString("name");
            String cond = obj.getString("conditionExpression");
            String sev = obj.getString("defaultSeverity");
            String desc = obj.getString("description");

            if (code != null && !code.isEmpty()) {
                builder.code(code);
            } else {
                builder.code("ai-" + Math.abs(fallbackDesc.hashCode()));
            }
            if (name != null && !name.isEmpty()) {
                builder.name(name);
            } else {
                builder.name(fallbackDesc);
            }
            if (desc != null && !desc.isEmpty()) {
                builder.description(desc);
            }
            if (sev != null && !sev.isEmpty()) {
                try {
                    builder.defaultSeverity(RuleSeverity.valueOf(sev.toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    builder.defaultSeverity(RuleSeverity.YELLOW);
                }
            } else {
                builder.defaultSeverity(RuleSeverity.YELLOW);
            }
            if (cond != null && !cond.isEmpty()) {
                ExpressionValidationResult v = expressionValidator.validateCondition(cond);
                if (v != null && v.isValid()) {
                    builder.conditionExpression(cond);
                } else {
                    log.warn("[LLM] 生成的表达式未通过校验: expr={} reason={}",
                            cond, v == null ? "null" : v.getErrorMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[LLM] 解析规则 JSON 失败: raw={} err={}", raw, e.getMessage());
        }
        return builder.build();
    }

    private String buildRulePrompt(RuleDefinition rule) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", rule.getCode());
        m.put("name", rule.getName());
        m.put("category", rule.getCategory());
        m.put("conditionExpression", rule.getConditionExpression());
        m.put("severityExpression", rule.getSeverityExpression());
        m.put("defaultSeverity", rule.getDefaultSeverity() == null ? null : rule.getDefaultSeverity().name());
        m.put("description", rule.getDescription());
        return com.alibaba.fastjson2.JSON.toJSONString(m);
    }

    private String extractJsonBlock(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }
}
