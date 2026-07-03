package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.DecisionTableRule;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * 规则热加载管理器
 *
 * <p>监听 {@link RuleConfigRefreshEvent} 事件，从 {@link RuleConfigProvider} 重新加载规则定义，
 * 构建 {@link ExpressionRule} 并注册到引擎，实现运行时规则热刷新。
 *
 * <p>1.4.0 起支持决策表热加载：当 {@link DecisionTableConfigProvider} 可用时，
 * 同步加载决策表并注册为 {@link DecisionTableRule}。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class RuleHotReloader {

    private final RuleEngine ruleEngine;
    private final ExpressionEvaluator evaluator;
    private final RuleConfigProvider configProvider;
    private final LiteRuleProperties properties;

    /** 决策表配置提供者（可选，1.4.0 起支持） */
    private DecisionTableConfigProvider decisionTableConfigProvider;

    /**
     * 设置决策表配置提供者
     *
     * @param provider 决策表配置提供者
     * @since 1.4.0
     */
    public void setDecisionTableConfigProvider(DecisionTableConfigProvider provider) {
        this.decisionTableConfigProvider = provider;
    }

    /**
     * 启动时全量加载规则
     */
    @PostConstruct
    public void initLoad() {
        if (!properties.isHotReloadEnabled()) {
            log.info("[LiteRule] 热加载已禁用，跳过初始加载");
            return;
        }
        if (!properties.isAutoRegisterBuiltinRules()) {
            log.info("[LiteRule] 自动注册内置规则已禁用，跳过初始加载");
            return;
        }
        fullReload("SYSTEM_INIT");
    }

    /**
     * 全量重新加载规则
     *
     * @param operator 操作人
     */
    public void fullReload(String operator) {
        try {
            // 先注销所有动态加载的规则（保留编程式注册的 StaticRule）
            for (Rule existing : ruleEngine.getRules()) {
                if (existing instanceof ExpressionRule || existing instanceof DecisionTableRule) {
                    ruleEngine.unregister(existing.getCode());
                }
            }

            // 加载表达式规则
            int exprCount = 0;
            List<RuleDefinition> definitions = configProvider.loadEnabledRules();
            for (RuleDefinition def : definitions) {
                if (!def.isEnabled()) continue;
                try {
                    ExpressionRule rule = new ExpressionRule(def, evaluator);
                    ruleEngine.register(rule);
                    exprCount++;
                } catch (Exception e) {
                    log.warn("[LiteRule] 规则 {} 加载失败: {}", def.getCode(), e.getMessage());
                }
            }

            // 加载决策表
            int dtCount = 0;
            if (decisionTableConfigProvider != null) {
                List<DecisionTableDefinition> tables = decisionTableConfigProvider.loadEnabledTables();
                for (DecisionTableDefinition dt : tables) {
                    if (!dt.isEnabled()) continue;
                    try {
                        DecisionTableRule rule = new DecisionTableRule(dt, evaluator);
                        ruleEngine.register(rule);
                        dtCount++;
                    } catch (Exception e) {
                        log.warn("[LiteRule-DecisionTable] 决策表 {} 加载失败: {}", dt.getTableCode(), e.getMessage());
                    }
                }
            }

            log.info("[LiteRule] 全量热刷新完成: 表达式规则 {} 条, 决策表 {} 个, operator={}",
                    exprCount, dtCount, operator);
        } catch (Exception e) {
            log.error("[LiteRule] 全量热刷新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 监听规则配置变更事件
     *
     * @param event 刷新事件
     */
    @EventListener
    @Order(100)
    public void onConfigRefresh(RuleConfigRefreshEvent event) {
        if (!properties.isHotReloadEnabled()) return;
        log.info("[LiteRule] 收到规则变更事件: type={}, ruleCode={}, operator={}",
                event.getChangeType(), event.getRuleCode(), event.getOperator());

        switch (event.getChangeType()) {
            case FULL_RELOAD -> fullReload(event.getOperator());
            case DELETE -> {
                ruleEngine.unregister(event.getRuleCode());
                log.info("[LiteRule] 规则/决策表已注销: code={}, operator={}",
                        event.getRuleCode(), event.getOperator());
            }
            default -> reloadSingle(event.getRuleCode(), event.getOperator());
        }
    }

    /**
     * 重新加载单条规则（先尝试表达式规则，再尝试决策表）
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     */
    private void reloadSingle(String ruleCode, String operator) {
        try {
            // 优先尝试表达式规则
            RuleDefinition def = configProvider.findByCode(ruleCode);
            if (def != null) {
                if (!def.isEnabled()) {
                    ruleEngine.unregister(ruleCode);
                    log.info("[LiteRule] 规则 {} 已注销（已禁用）, operator={}", ruleCode, operator);
                    return;
                }
                ExpressionRule rule = new ExpressionRule(def, evaluator);
                ruleEngine.register(rule);
                log.info("[LiteRule] 规则 {} 热刷新完成, operator={}", ruleCode, operator);
                return;
            }

            // 回退到决策表
            if (decisionTableConfigProvider != null) {
                DecisionTableDefinition dt = decisionTableConfigProvider.findByCode(ruleCode);
                if (dt != null) {
                    if (!dt.isEnabled()) {
                        ruleEngine.unregister(ruleCode);
                        log.info("[LiteRule-DecisionTable] 决策表 {} 已注销（已禁用）, operator={}",
                                ruleCode, operator);
                        return;
                    }
                    DecisionTableRule rule = new DecisionTableRule(dt, evaluator);
                    ruleEngine.register(rule);
                    log.info("[LiteRule-DecisionTable] 决策表 {} 热刷新完成, operator={}", ruleCode, operator);
                    return;
                }
            }

            // 既非表达式规则也非决策表：注销
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule] 规则 {} 未找到，已注销, operator={}", ruleCode, operator);
        } catch (Exception e) {
            log.error("[LiteRule] 规则 {} 热刷新失败: {}", ruleCode, e.getMessage(), e);
        }
    }
}
