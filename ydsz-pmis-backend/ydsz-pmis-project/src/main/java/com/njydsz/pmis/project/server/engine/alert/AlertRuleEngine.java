package com.njydsz.pmis.project.server.engine.alert;

import com.njydsz.pmis.project.domain.dto.AlertEventDTO;
import com.njydsz.pmis.project.domain.enums.AlertSeverity;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 预警规则引擎
 *
 * <p>遍历注册的全部规则，输入 KPI 快照（Map）评估，收集触发的预警事件。
 * 单一规则实现异常不影响其他规则。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class AlertRuleEngine {

    /** 已注册规则列表 */
    private final List<AlertRule> rules = new ArrayList<>();

    /**
     * 注册一条规则
     *
     * @param rule 预警规则
     * @return 当前引擎实例（链式调用）
     */
    public AlertRuleEngine register(AlertRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
        return this;
    }

    /**
     * 批量注册
     *
     * @param ruleList 规则列表
     * @return 当前引擎实例（链式调用）
     */
    public AlertRuleEngine registerAll(List<AlertRule> ruleList) {
        if (ruleList != null) {
            rules.addAll(ruleList);
        }
        return this;
    }

    /**
     * 获取全部已注册规则（只读）
     *
     * @return 不可修改的规则列表
     */
    public List<AlertRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 评估 KPI 快照，返回全部触发的预警事件。
     *
     * <p>按严重度倒序（RED → YELLOW → INFO）排列，便于前端 banner 优先展示严重项。
     *
     * @param snapshot KPI 快照（必须包含规则所需字段，缺失时取默认值 0 / null）
     * @return 触发的预警事件列表（无任何触发时返回空列表，永不为 null）
     */
    public List<AlertEventDTO> evaluate(Map<String, Object> snapshot) {
        List<AlertEventDTO> out = new ArrayList<>();
        for (AlertRule rule : rules) {
            try {
                AlertEventDTO event = rule.evaluate(snapshot);
                if (event != null) {
                    out.add(event);
                }
            } catch (Exception e) {
                log.warn("[AlertEngine] 规则 {} 评估失败: {}", rule.getCode(), e.getMessage());
            }
        }
        // 按严重度倒序
        out.sort(Comparator.comparingInt((AlertEventDTO e) -> severityWeight(e)).reversed());
        return out;
    }

    /**
     * 仅返回最高严重度的预警（用于顶部 banner 摘要）
     *
     * @param snapshot KPI 快照
     * @return 最高严重度预警事件；无预警返回 null
     */
    public AlertEventDTO topAlert(Map<String, Object> snapshot) {
        List<AlertEventDTO> all = evaluate(snapshot);
        if (all.isEmpty()) return null;
        return all.get(0);
    }

    /**
     * 严重度数值化（用于排序）
     *
     * @param e 预警事件
     * @return 严重度权重值
     */
    public static int severityWeight(AlertEventDTO e) {
        if (e == null || e.getSeverity() == null) return 0;
        return e.getSeverity().getWeight();
    }

    /**
     * 严重度数值化（字符串 code）
     *
     * @param code 严重度编码
     * @return 严重度权重值
     */
    public static int severityWeightByCode(String code) {
        AlertSeverity s = AlertSeverity.fromCode(code);
        return s == null ? 0 : s.getWeight();
    }
}
