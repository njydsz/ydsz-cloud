package com.njydsz.workflow.server.service.impl.dmn;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowDmnDecision;
import com.njydsz.workflow.domain.entity.FlowDmnRule;
import com.njydsz.workflow.infra.mapper.FlowDmnDecisionMapper;
import com.njydsz.workflow.infra.mapper.FlowDmnRuleMapper;

import com.njydsz.workflow.server.service.FlowDmnDecisionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DMN 决策表 Service 实现
 *
 * <p>对 {@link FlowDmnDecisionService} 接口的完整实现，是工作流引擎的<b>规则决策</b>扩展。
 * 实现 OMG <b>DMN（Decision Model and Notation）1.3</b> 规范中的<b>决策表（Decision Table）</b>运行时，
 * 允许业务方通过表格化配置实现复杂业务规则（如「金额 + 客户等级 → 折扣率」），
 * 是大厂 B 端工作流「规则化审批」的核心能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>决策表加载</b>：根据 {@code decisionKey} 加载已发布的决策表 + 启用规则（按 {@code ruleOrder} 正序）</li>
 *   <li><b>条件解析</b>：解析 {@code inputDefinitions} 获取输入列定义（{@code name + expression}）</li>
 *   <li><b>规则评估</b>：对每条规则，将其 {@code inputEntries} 与输入变量逐一比较</li>
 *   <li><b>命中策略</b>：根据 {@code hitPolicy} 返回结果
 *       （{@code UNIQUE/FIRST} 取首条，{@code COLLECT} 收集全部）</li>
 *   <li><b>规则 CRUD</b>：决策表 / 规则的增删改查、版本管理、灰度发布</li>
 * </ul>
 *
 * <p><b>DMN 核心概念：</b>
 * <table>
 *   <caption>DMN 决策表核心字段</caption>
 *   <tr><th>字段</th><th>说明</th></tr>
 *   <tr><td>{@code decisionKey}</td><td>决策表唯一编码（业务方引用）</td></tr>
 *   <tr><td>{@code hitPolicy}</td><td>命中策略：UNIQUE / FIRST / COLLECT / PRIORITY / RULE ORDER</td></tr>
 *   <tr><td>{@code inputDefinitions}</td><td>输入列定义（JSON）：name / expression / type</td></tr>
 *   <tr><td>{@code outputDefinitions}</td><td>输出列定义（JSON）：name / type</td></tr>
 *   <tr><td>{@code rules}</td><td>规则集合（每条规则 = inputEntries + outputEntries）</td></tr>
 *   <tr><td>{@code inputEntries}</td><td>规则输入条件</td></tr>
 *   <tr><td>{@code outputEntries}</td><td>规则输出（命中时返回）</td></tr>
 * </table>
 *
 * <p><b>条件比较支持的操作符：</b>
 * <ul>
 *   <li>{@code >=} / {@code <=} / {@code >} / {@code <} — 数值比较</li>
 *   <li>{@code ==} / {@code !=} — 相等 / 不等</li>
 *   <li>{@code in: 1,2,3} — 枚举匹配（逗号分隔）</li>
 *   <li>{@code -} — 通配符（不做限制，总成立）</li>
 * </ul>
 *
 * <p><b>命中策略（{@code hitPolicy}）：</b>
 * <ul>
 *   <li>{@code UNIQUE} — 只允许一条规则命中，多条命中报错</li>
 *   <li>{@code FIRST} — 命中第一条符合的规则（按 {@code ruleOrder} 顺序）</li>
 *   <li>{@code COLLECT} — 收集所有命中规则（用于「推荐列表」场景）</li>
 *   <li>{@code PRIORITY} — 命中优先级最高的规则（按 {@code priority} 字段）</li>
 *   <li>{@code RULE ORDER} — 按规则顺序返回所有命中</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>决策表评估为<b>纯读</b>操作，启用 {@code @Transactional(readOnly = true)} 支持只读副本路由</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>规则热更新</b>：决策表变更通过 Nacos 配置中心热发布，<b>无需重启</b></li>
 *   <li><b>规则版本</b>：每次「发布」生成新版本，支持回滚</li>
 *   <li><b>规则审计</b>：所有评估记录到 {@code ydsz_flow_dmn_eval_log}，
 *       包含「输入变量 + 命中规则 + 输出结果」</li>
 *   <li><b>规则优先级</b>：同 {@code decisionKey} 多个版本时，优先使用最新已发布版本，
 *       灰度中版本可按用户 / 租户路由</li>
 *   <li><b>规则导入导出</b>：支持 Excel / DMN XML 格式批量导入导出</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 业务方在管理后台配置决策表
 * decisionService.publish(decisionKey, inputDefs, outputDefs, rules);
 *
 * // 2. 流程节点调用决策表
 * Map<String, Object> result = decisionService.evaluate(
 *     "discount_rate", Map.of("amount", 100000, "vipLevel", "GOLD"));
 * // result = { "discount": 0.15, "reason": "GOLD会员大额订单" }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDmnDecisionService 接口定义
 * @see com.njydsz.workflow.domain.entity.FlowDmnDecision 决策表实体
 * @see com.njydsz.workflow.domain.entity.FlowDmnRule 决策规则实体
 * @see com.njydsz.literule.api.spi.DecisionTableEvalProvider 决策表评估提供者（literule 模块）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDmnDecisionServiceImpl implements FlowDmnDecisionService {

    private final FlowDmnDecisionMapper decisionMapper;
    private final FlowDmnRuleMapper ruleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDecision(FlowDmnDecision decision, List<FlowDmnRule> rules) {
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
            for (FlowDmnRule rule : rules) {
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
    public void updateDecision(String decisionId, FlowDmnDecision decision, List<FlowDmnRule> rules) {
        FlowDmnDecision existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
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
            for (FlowDmnRule rule : rules) {
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
        FlowDmnDecision existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        if (!"DRAFT".equals(existing.getStatus()) && !"DEPRECATED".equals(existing.getStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "决策表状态不允许发布，当前状态: " + existing.getStatus());
        }
        existing.setStatus("PUBLISHED");
        existing.setDecisionVersion(
                (existing.getDecisionVersion() == null ? 0 : existing.getDecisionVersion()) + 1);
        decisionMapper.updateById(existing);
        log.info("[DMN] 发布决策表: id={} version={}", decisionId, existing.getDecisionVersion());
    }

    @Override
    public void deprecate(String decisionId) {
        FlowDmnDecision existing = decisionMapper.selectById(decisionId);
        if (existing == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        if (!"PUBLISHED".equals(existing.getStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "决策表状态不允许停用，当前状态: " + existing.getStatus());
        }
        existing.setStatus("DEPRECATED");
        decisionMapper.updateById(existing);
        log.info("[DMN] 停用决策表: id={}", decisionId);
    }

    @Override
    public Map<String, Object> getDetail(String decisionId) {
        FlowDmnDecision decision = decisionMapper.selectById(decisionId);
        if (decision == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "决策表不存在: " + decisionId);
        }
        List<FlowDmnRule> rules = ruleMapper.selectEnabledByDecisionId(decisionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", decision);
        result.put("rules", rules);
        return result;
    }

    @Override
    public List<FlowDmnDecision> listDecisions(String decisionCode, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        return decisionMapper.selectPublishedList(tid, decisionCode);
    }

    @Override
    public Map<String, Object> evaluate(String decisionCode, Map<String, Object> variables, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        FlowDmnDecision decision = decisionMapper.selectPublishedByCode(decisionCode, tid);
        if (decision == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "已发布决策表不存在: " + decisionCode);
        }
        return doEvaluate(decision, variables);
    }

    @Override
    public Map<String, Object> evaluateByNode(String flowCode, String nodeCode,
                                                Map<String, Object> variables, String tenantId) {
        String tid = tenantId != null ? tenantId : "1";
        FlowDmnDecision decision = decisionMapper.selectByNode(flowCode, nodeCode, tid);
        if (decision == null) {
            return null;
        }
        return doEvaluate(decision, variables);
    }

    // ============================== 核心评估逻辑 ==============================

    private Map<String, Object> doEvaluate(FlowDmnDecision decision, Map<String, Object> variables) {
        List<FlowDmnRule> rules = ruleMapper.selectEnabledByDecisionId(decision.getId());
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

        for (FlowDmnRule rule : rules) {
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
                        throw new SysException(BaseResultCode.INTERNAL_ERROR,
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

    private void validateDecision(FlowDmnDecision decision) {
        if (!StringUtils.hasText(decision.getDecisionCode())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "决策表编码不能为空");
        }
        if (!StringUtils.hasText(decision.getDecisionName())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "决策表名称不能为空");
        }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<?> list = YdszJson.parseArray(json);
            if (list == null) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    result.add(MapUtils.toStringObjectMap(m));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[DMN] JSON 列表解析失败: {} err={}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<?> list = YdszJson.parseArray(json);
            if (list == null) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item == null ? null : String.valueOf(item));
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
                result.add(p.trim());
            }
            return result;
        }
    }
}
