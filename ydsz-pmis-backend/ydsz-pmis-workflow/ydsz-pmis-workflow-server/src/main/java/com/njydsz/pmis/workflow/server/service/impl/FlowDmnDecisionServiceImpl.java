package com.njydsz.pmis.workflow.server.service.impl.dmn;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.workflow.domain.entity.dmn.FlowDmnDecisionDO;
import com.njydsz.pmis.workflow.domain.entity.dmn.FlowDmnRuleDO;
import com.njydsz.pmis.workflow.infra.mapper.dmn.FlowDmnDecisionMapper;
import com.njydsz.pmis.workflow.infra.mapper.dmn.FlowDmnRuleMapper;
import com.njydsz.pmis.workflow.server.service.dmn.FlowDmnDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * P0-1: DMN 决策表 Service 实现
 *
 * <p>核心评估逻辑：
 * <ol>
 *   <li>加载已发布决策表 + 启用规则（按 ruleOrder 正序）</li>
 *   <li>解析 inputDefinitions 获取输入列定义（name + expression）</li>
 *   <li>对每条规则，将其 inputEntries 与输入变量逐一比较</li>
 *   <li>根据 hitPolicy 返回结果（UNIQUE/FIRST 取首条，COLLECT 收集全部）</li>
 * </ol>
 *
 * <p>条件比较支持的操作符：{@code >=}, {@code <=}, {@code >}, {@code <}, {@code ==}, {@code !=},
 * {@code in:}（逗号分隔枚举）。"-"表示通配（不做限制）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDmnDecisionServiceImpl implements FlowDmnDecisionService {

    private final FlowDmnDecisionMapper decisionMapper;
    private final FlowDmnRuleMapper ruleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDecision(FlowDmnDecisionDO decision, List<FlowDmnRuleDO> rules) {
        validateDecision(decision);
        if (decision.getHitPolicy() == null) {
            decision.setHitPolicy("FIRST");
        }
        decision.setStatus("DRAFT");
        decision.setDecisionVersion(1);
        if (decision.getTenantId() == null) {
            decision.setTenantId("1");
        }
        decisionMapper.insert(decision);
        if (rules != null) {
            int order = 1;
            for (FlowDmnRuleDO rule : rules) {
                rule.setDecisionId(decision.getId());
                rule.setRuleOrder(order++);
                rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
                if (rule.getTenantId() == null) {
                    rule.setTenantId(decision.getTenantId());
                }
                ruleMapper.insert(rule);
            }
        }
        log.info("[DMN] 创建决策表: code={} id={} ruleCount={}",
                decision.getDecisionCode(), decision.getId(),
                rules == null ? 0 : rules.size());
        return decision.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDecision(String decisionId, FlowDmnDecisionDO decision, List<FlowDmnRuleDO> rules) {
        FlowDmnDecisionDO existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "仅草稿状态可编辑，当前状态: " + existing.getStatus());
        }
        decision.setId(decisionId);
        decision.setStatus("DRAFT");
        decision.setDecisionVersion(existing.getDecisionVersion());
        decisionMapper.updateById(decision);
        // 重建规则
        ruleMapper.deleteByDecisionId(decisionId);
        if (rules != null) {
            int order = 1;
            for (FlowDmnRuleDO rule : rules) {
                rule.setDecisionId(decisionId);
                rule.setRuleOrder(order++);
                rule.setEnabled(rule.getEnabled() == null ? 1 : rule.getEnabled());
                if (rule.getTenantId() == null) {
                    rule.setTenantId(existing.getTenantId());
                }
                ruleMapper.insert(rule);
            }
        }
        log.info("[DMN] 更新决策表: id={} ruleCount={}", decisionId,
                rules == null ? 0 : rules.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(String decisionId) {
        FlowDmnDecisionDO existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        existing.setStatus("PUBLISHED");
        existing.setDecisionVersion(
                (existing.getDecisionVersion() == null ? 0 : existing.getDecisionVersion()) + 1);
        decisionMapper.updateById(existing);
        log.info("[DMN] 发布决策表: id={} version={}", decisionId, existing.getDecisionVersion());
    }

    @Override
    public void deprecate(String decisionId) {
        FlowDmnDecisionDO existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        existing.setStatus("DEPRECATED");
        decisionMapper.updateById(existing);
        log.info("[DMN] 停用决策表: id={}", decisionId);
    }

    @Override
    public Map<String, Object> getDetail(String decisionId) {
        FlowDmnDecisionDO decision = decisionMapper.selectById(decisionId);
        if (decision == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        List<FlowDmnRuleDO> rules = ruleMapper.selectEnabledByDecisionId(decisionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", decision);
        result.put("rules", rules);
        return result;
    }

    @Override
    public List<FlowDmnDecisionDO> listDecisions(String decisionCode, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        return decisionMapper.selectPublishedList(tid, decisionCode);
    }

    @Override
    public Map<String, Object> evaluate(String decisionCode, Map<String, Object> variables, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        FlowDmnDecisionDO decision = decisionMapper.selectPublishedByCode(decisionCode, tid);
        if (decision == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "已发布决策表不存在: " + decisionCode);
        }
        return doEvaluate(decision, variables);
    }

    @Override
    public Map<String, Object> evaluateByNode(String flowCode, String nodeCode,
                                                Map<String, Object> variables, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        FlowDmnDecisionDO decision = decisionMapper.selectByNode(flowCode, nodeCode, tid);
        if (decision == null) {
            return null;
        }
        return doEvaluate(decision, variables);
    }

    // ============================== 核心评估逻辑 ==============================

    private Map<String, Object> doEvaluate(FlowDmnDecisionDO decision, Map<String, Object> variables) {
        List<FlowDmnRuleDO> rules = ruleMapper.selectEnabledByDecisionId(decision.getId());
        if (rules == null || rules.isEmpty()) {
            log.warn("[DMN] 决策表无启用规则: code={}", decision.getDecisionCode());
            return Collections.emptyMap();
        }

        // 解析输入定义
        List<Map<String, Object>> inputDefs = parseJsonList(decision.getInputDefinitions());
        // 解析输出定义
        List<Map<String, Object>> outputDefs = parseJsonList(decision.getOutputDefinitions());

        String hitPolicy = decision.getHitPolicy() != null ? decision.getHitPolicy() : "FIRST";
        List<Map<String, Object>> matchedOutputs = new ArrayList<>();

        for (FlowDmnRuleDO rule : rules) {
            List<String> inputEntries = parseStringList(rule.getInputEntries());
            if (matchRule(inputDefs, inputEntries, variables)) {
                List<String> outputEntries = parseStringList(rule.getOutputEntries());
                Map<String, Object> output = buildOutput(outputDefs, outputEntries);
                matchedOutputs.add(output);
                if ("UNIQUE".equals(hitPolicy) || "FIRST".equals(hitPolicy)) {
                    log.debug("[DMN] 规则命中 ({}): decision={} ruleOrder={} output={}",
                            hitPolicy, decision.getDecisionCode(), rule.getRuleOrder(), output);
                    return output;
                }
                if ("ANY".equals(hitPolicy) && matchedOutputs.size() > 1) {
                    // ANY 策略：校验所有命中规则输出一致
                    if (!Objects.equals(matchedOutputs.get(0), output)) {
                        throw new SysException(StandardResultCode.INTERNAL_ERROR,
                                "DMN ANY 策略校验失败: 多条命中规则输出不一致");
                    }
                }
            }
        }

        if (matchedOutputs.isEmpty()) {
            log.debug("[DMN] 无规则命中: decision={}", decision.getDecisionCode());
            return Collections.emptyMap();
        }

        // COLLECT 策略：收集所有命中输出
        Map<String, Object> collectResult = new LinkedHashMap<>();
        for (Map<String, Object> def : outputDefs) {
            String name = String.valueOf(def.get("name"));
            List<Object> values = matchedOutputs.stream()
                    .map(o -> o.get(name))
                    .toList();
            collectResult.put(name, values);
        }
        log.debug("[DMN] COLLECT 策略命中 {} 条规则: decision={}",
                matchedOutputs.size(), decision.getDecisionCode());
        return collectResult;
    }

    /**
     * 匹配单条规则的所有输入条件
     */
    private boolean matchRule(List<Map<String, Object>> inputDefs, List<String> inputEntries,
                              Map<String, Object> variables) {
        if (inputEntries == null || inputEntries.isEmpty()) {
            return true; // 无条件 = 总是匹配
        }
        for (int i = 0; i < inputEntries.size(); i++) {
            String condition = inputEntries.get(i);
            if (condition == null || "-".equals(condition.trim()) || condition.isBlank()) {
                continue; // 通配
            }
            // 获取输入变量值
            Object inputValue = null;
            if (i < inputDefs.size()) {
                Map<String, Object> def = inputDefs.get(i);
                String expr = String.valueOf(def.getOrDefault("expression", def.get("name")));
                inputValue = variables != null ? variables.get(expr) : null;
            }
            if (!matchCondition(condition.trim(), inputValue)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 评估单个条件表达式
     *
     * <p>支持: {@code >=1000}, {@code <=5000}, {@code >100}, {@code <50},
     * {@code ==value}, {@code !=value}, {@code in:a,b,c}
     */
    private boolean matchCondition(String condition, Object inputValue) {
        if (inputValue == null) {
            return false;
        }
        try {
            if (condition.startsWith("in:")) {
                String[] parts = condition.substring(3).split(",");
                String valStr = String.valueOf(inputValue);
                for (String p : parts) {
                    if (valStr.equals(p.trim())) {
                        return true;
                    }
                }
                return false;
            }
            if (condition.startsWith(">=")) {
                return compareNumeric(inputValue, condition.substring(2)) >= 0;
            }
            if (condition.startsWith("<=")) {
                return compareNumeric(inputValue, condition.substring(2)) <= 0;
            }
            if (condition.startsWith("!=")) {
                return !String.valueOf(inputValue).equals(condition.substring(2).trim());
            }
            if (condition.startsWith(">")) {
                return compareNumeric(inputValue, condition.substring(1)) > 0;
            }
            if (condition.startsWith("<")) {
                return compareNumeric(inputValue, condition.substring(1)) < 0;
            }
            if (condition.startsWith("==")) {
                return String.valueOf(inputValue).equals(condition.substring(2).trim());
            }
            // 无操作符 = 等值比较
            return String.valueOf(inputValue).equals(condition.trim());
        } catch (Exception e) {
            log.warn("[DMN] 条件评估异常: condition={} value={} err={}",
                    condition, inputValue, e.getMessage());
            return false;
        }
    }

    /**
     * 数值比较，返回 -1/0/1
     */
    private int compareNumeric(Object inputValue, String conditionValue) {
        BigDecimal val1 = toBigDecimal(inputValue);
        BigDecimal val2 = toBigDecimal(conditionValue.trim());
        return val1.compareTo(val2);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    /**
     * 构建输出 Map
     */
    private Map<String, Object> buildOutput(List<Map<String, Object>> outputDefs, List<String> outputEntries) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (outputDefs == null || outputEntries == null) {
            return output;
        }
        for (int i = 0; i < outputDefs.size() && i < outputEntries.size(); i++) {
            String name = String.valueOf(outputDefs.get(i).get("name"));
            String type = String.valueOf(outputDefs.get(i).getOrDefault("type", "string"));
            String rawValue = outputEntries.get(i);
            output.put(name, convertValue(rawValue, type));
        }
        return output;
    }

    private Object convertValue(String rawValue, String type) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return switch (type) {
            case "number" -> {
                try {
                    yield new BigDecimal(trimmed);
                } catch (NumberFormatException e) {
                    yield trimmed;
                }
            }
            case "boolean" -> Boolean.parseBoolean(trimmed);
            default -> trimmed;
        };
    }

    // ============================== 辅助方法 ==============================

    private void validateDecision(FlowDmnDecisionDO decision) {
        if (!StringUtils.hasText(decision.getDecisionCode())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "决策表编码不能为空");
        }
        if (!StringUtils.hasText(decision.getDecisionName())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "决策表名称不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<?> list = JsonUtils.parseList(json);
            if (list == null) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    BaseResponse.add((Map<String, Object>) m);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[DMN] JSON 列表解析失败: {} err={}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<?> list = JsonUtils.parseList(json);
            if (list == null) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                BaseResponse.add(item == null ? null : String.valueOf(item));
            }
            return result;
        } catch (Exception e) {
            // 尝试逗号分隔
            if (json.startsWith("[")) {
                log.warn("[DMN] JSON 字符串列表解析失败: {} err={}", json, e.getMessage());
                return Collections.emptyList();
            }
            String[] parts = json.split(",");
            List<String> result = new ArrayList<>();
            for (String p : parts) {
                BaseResponse.add(p.trim());
            }
            return result;
        }
    }
}
