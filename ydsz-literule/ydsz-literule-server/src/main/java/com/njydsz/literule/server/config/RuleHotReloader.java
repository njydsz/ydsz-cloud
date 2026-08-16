package com.njydsz.literule.server.config;

import java.util.List;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.njydsz.literule.api.DecisionTableDefinition;
import com.njydsz.literule.api.DecisionTreeDefinition;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.ScorecardDefinition;
import com.njydsz.literule.api.ScriptDefinition;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.literule.server.impl.DecisionTableRule;
import com.njydsz.literule.server.impl.DecisionTreeRule;
import com.njydsz.literule.server.impl.ExpressionRule;
import com.njydsz.literule.server.impl.ScorecardRule;
import com.njydsz.literule.server.impl.ScriptRule;
import com.njydsz.literule.server.spi.DecisionTableConfigProvider;
import com.njydsz.literule.server.spi.DecisionTreeConfigProvider;
import com.njydsz.literule.server.spi.RuleConfigProvider;
import com.njydsz.literule.server.spi.ScorecardConfigProvider;
import com.njydsz.literule.server.spi.ScriptConfigProvider;

/**
 * 规则热加载管理器
 *
 * <p>监听 {@link RuleConfigRefreshEvent} 事件，从 SPI Provider 重新加载规则定义，
 * 构建对应 {@link Rule} 实例并注册到引擎，实现运行时规则热刷新。
 *
 * <p>1.4.0 起支持以下规则类型的动态加载：
 * <ul>
 *   <li>表达式规则（{@link ExpressionRule}，必需 SPI：{@link RuleConfigProvider}）</li>
 *   <li>决策表规则（{@link DecisionTableRule}，可选 SPI：{@link DecisionTableConfigProvider}）</li>
 *   <li>评分卡规则（{@link ScorecardRule}，可选 SPI：{@link ScorecardConfigProvider}）</li>
 *   <li>决策树规则（{@link DecisionTreeRule}，可选 SPI：{@link DecisionTreeConfigProvider}）</li>
 *   <li>脚本规则（{@link ScriptRule}，可选 SPI：{@link ScriptConfigProvider}）</li>
 * </ul>
 *
 * <p>当 SPI Bean 不存在时，对应规则类型不会被加载（向后兼容）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@RequiredArgsConstructor
public class RuleHotReloader {

    /** 规则引擎实例，热加载后将构建的 Rule 实例注册/注销到引擎 */
    private final RuleEngine ruleEngine;
    /** 表达式求值器，用于构建表达式规则（ExpressionRule） */
    private final ExpressionEngine evaluator;
    /** 规则配置提供者（SPI），从数据库/配置中心加载规则定义 */
    private final RuleConfigProvider configProvider;
    /** LiteRule 配置属性，控制 dry-run、热加载开关等行为 */
    private final LiteRuleProperties properties;

    /** 决策表配置提供者（可选，1.4.0 起支持） */
    private DecisionTableConfigProvider decisionTableConfigProvider;

    /** 评分卡配置提供者（可选，1.4.0 起支持） */
    private ScorecardConfigProvider scorecardConfigProvider;

    /** 决策树配置提供者（可选，1.4.0 起支持） */
    private DecisionTreeConfigProvider decisionTreeConfigProvider;

    /** 脚本规则配置提供者（可选，1.4.0 起支持） */
    private ScriptConfigProvider scriptConfigProvider;

    /**
     * 设置决策表配置提供者
     *
     * @param provider 决策表配置提供者
     * @since 1.0.0
     */
    public void setDecisionTableConfigProvider(DecisionTableConfigProvider provider) {
        this.decisionTableConfigProvider = provider;
    }

    /**
     * 设置评分卡配置提供者
     *
     * @param provider 评分卡配置提供者
     * @since 1.0.0
     */
    public void setScorecardConfigProvider(ScorecardConfigProvider provider) {
        this.scorecardConfigProvider = provider;
    }

    /**
     * 设置决策树配置提供者
     *
     * @param provider 决策树配置提供者
     * @since 1.0.0
     */
    public void setDecisionTreeConfigProvider(DecisionTreeConfigProvider provider) {
        this.decisionTreeConfigProvider = provider;
    }

    /**
     * 设置脚本规则配置提供者
     *
     * @param provider 脚本配置提供者
     * @since 1.0.0
     */
    public void setScriptConfigProvider(ScriptConfigProvider provider) {
        this.scriptConfigProvider = provider;
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
                if (isDynamicRule(existing)) {
                    ruleEngine.unregister(existing.getCode());
                }
            }

            int exprCount = loadExpressionRules();
            int dtCount = loadDecisionTables();
            int scCount = loadScorecards();
            int trCount = loadDecisionTrees();
            int sc2Count = loadScripts();

            log.info("[LiteRule] 全量热刷新完成: 表达式规则 {}, 决策表 {}, 评分卡 {}, 决策树 {}, 脚本 {}, operator={}",
                    exprCount, dtCount, scCount, trCount, sc2Count, operator);
        } catch (Exception e) {
            log.error("[LiteRule] 全量热刷新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 判断规则是否为动态加载类型（用于注销时识别）
     */
    private boolean isDynamicRule(Rule rule) {
        return rule instanceof ExpressionRule
                || rule instanceof DecisionTableRule
                || rule instanceof ScorecardRule
                || rule instanceof DecisionTreeRule
                || rule instanceof ScriptRule;
    }

    private int loadExpressionRules() {
        int count = 0;
        List<RuleDefinition> definitions = configProvider.loadEnabledRules();
        for (RuleDefinition def : definitions) {
            if (!def.isEnabled()) continue;
            try {
                ruleEngine.register(new ExpressionRule(def, evaluator));
                count++;
            } catch (Exception e) {
                log.warn("[LiteRule] 规则 {} 加载失败: {}", def.getCode(), e.getMessage());
            }
        }
        return count;
    }

    private int loadDecisionTables() {
        if (decisionTableConfigProvider == null) return 0;
        int count = 0;
        for (DecisionTableDefinition dt : decisionTableConfigProvider.loadEnabledTables()) {
            if (!dt.isEnabled()) continue;
            try {
                ruleEngine.register(new DecisionTableRule(dt, evaluator));
                count++;
            } catch (Exception e) {
                log.warn("[LiteRule-DecisionTable] 决策表 {} 加载失败: {}", dt.getTableCode(), e.getMessage());
            }
        }
        return count;
    }

    private int loadScorecards() {
        if (scorecardConfigProvider == null) return 0;
        int count = 0;
        for (ScorecardDefinition def : scorecardConfigProvider.loadEnabledScorecards()) {
            if (!def.isEnabled()) continue;
            try {
                ruleEngine.register(ScorecardRule.from(def, evaluator));
                count++;
            } catch (Exception e) {
                log.warn("[LiteRule-Scorecard] 评分卡 {} 加载失败: {}", def.getRuleCode(), e.getMessage());
            }
        }
        return count;
    }

    private int loadDecisionTrees() {
        if (decisionTreeConfigProvider == null) return 0;
        int count = 0;
        for (DecisionTreeDefinition def : decisionTreeConfigProvider.loadEnabledTrees()) {
            if (!def.isEnabled()) continue;
            try {
                ruleEngine.register(DecisionTreeRule.from(def, evaluator));
                count++;
            } catch (Exception e) {
                log.warn("[LiteRule-DecisionTree] 决策树 {} 加载失败: {}", def.getRuleCode(), e.getMessage());
            }
        }
        return count;
    }

    private int loadScripts() {
        if (scriptConfigProvider == null) return 0;
        int count = 0;
        for (ScriptDefinition def : scriptConfigProvider.loadEnabledScripts()) {
            if (!def.isEnabled()) continue;
            try {
                ruleEngine.register(ScriptRule.from(def));
                count++;
            } catch (Exception e) {
                log.warn("[LiteRule-Script] 脚本规则 {} 加载失败: {}", def.getRuleCode(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * 监听规则配置变更事件
     *
     * <p>使用 {@code @TransactionalEventListener(AFTER_COMMIT)} 确保仅在校验/持久化事务
     * 成功提交后才执行热加载，回滚时不触发（避免从 DB 读取到未提交的脏数据）。
     * {@code fallbackExecution=true} 确保非事务上下文（如 Redis 跨节点广播回调线程）
     * 中发布的事件仍能正常触发。
     *
     * @param event 刷新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Order(100)
    public void onConfigRefresh(RuleConfigRefreshEvent event) {
        if (!properties.isHotReloadEnabled()) return;
        log.info("[LiteRule] 收到规则变更事件: type={}, ruleCode={}, operator={}",
                event.getChangeType(), event.getRuleCode(), event.getOperator());

        switch (event.getChangeType()) {
            case FULL_RELOAD -> fullReload(event.getOperator());
            case DELETE -> {
                ruleEngine.unregister(event.getRuleCode());
                log.info("[LiteRule] 规则已注销: code={}, operator={}", event.getRuleCode(), event.getOperator());
            }
            default -> reloadSingle(event.getRuleCode(), event.getOperator());
        }
    }

    /**
     * 重新加载单条规则（按规则类型顺序尝试）
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     */
    private void reloadSingle(String ruleCode, String operator) {
        try {
            if (tryReloadExpression(ruleCode, operator)) return;
            if (tryReloadDecisionTable(ruleCode, operator)) return;
            if (tryReloadScorecard(ruleCode, operator)) return;
            if (tryReloadDecisionTree(ruleCode, operator)) return;
            if (tryReloadScript(ruleCode, operator)) return;

            // 既非表达式规则也非其他类型：注销
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule] 规则 {} 未找到，已注销, operator={}", ruleCode, operator);
        } catch (Exception e) {
            log.error("[LiteRule] 规则 {} 热刷新失败: {}", ruleCode, e.getMessage(), e);
        }
    }

    private boolean tryReloadExpression(String ruleCode, String operator) {
        RuleDefinition def = configProvider.findByCode(ruleCode);
        if (def == null) return false;
        if (!def.isEnabled()) {
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule] 规则 {} 已注销（已禁用）, operator={}", ruleCode, operator);
            return true;
        }
        ruleEngine.register(new ExpressionRule(def, evaluator));
        log.info("[LiteRule] 规则 {} 热刷新完成, operator={}", ruleCode, operator);
        return true;
    }

    private boolean tryReloadDecisionTable(String ruleCode, String operator) {
        if (decisionTableConfigProvider == null) return false;
        DecisionTableDefinition dt = decisionTableConfigProvider.findByCode(ruleCode);
        if (dt == null) return false;
        if (!dt.isEnabled()) {
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule-DecisionTable] 决策表 {} 已注销（已禁用）, operator={}", ruleCode, operator);
            return true;
        }
        ruleEngine.register(new DecisionTableRule(dt, evaluator));
        log.info("[LiteRule-DecisionTable] 决策表 {} 热刷新完成, operator={}", ruleCode, operator);
        return true;
    }

    private boolean tryReloadScorecard(String ruleCode, String operator) {
        if (scorecardConfigProvider == null) return false;
        ScorecardDefinition def = scorecardConfigProvider.findByCode(ruleCode);
        if (def == null) return false;
        if (!def.isEnabled()) {
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule-Scorecard] 评分卡 {} 已注销（已禁用）, operator={}", ruleCode, operator);
            return true;
        }
        ruleEngine.register(ScorecardRule.from(def, evaluator));
        log.info("[LiteRule-Scorecard] 评分卡 {} 热刷新完成, operator={}", ruleCode, operator);
        return true;
    }

    private boolean tryReloadDecisionTree(String ruleCode, String operator) {
        if (decisionTreeConfigProvider == null) return false;
        DecisionTreeDefinition def = decisionTreeConfigProvider.findByCode(ruleCode);
        if (def == null) return false;
        if (!def.isEnabled()) {
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule-DecisionTree] 决策树 {} 已注销（已禁用）, operator={}", ruleCode, operator);
            return true;
        }
        ruleEngine.register(DecisionTreeRule.from(def, evaluator));
        log.info("[LiteRule-DecisionTree] 决策树 {} 热刷新完成, operator={}", ruleCode, operator);
        return true;
    }

    private boolean tryReloadScript(String ruleCode, String operator) {
        if (scriptConfigProvider == null) return false;
        ScriptDefinition def = scriptConfigProvider.findByCode(ruleCode);
        if (def == null) return false;
        if (!def.isEnabled()) {
            ruleEngine.unregister(ruleCode);
            log.info("[LiteRule-Script] 脚本规则 {} 已注销（已禁用）, operator={}", ruleCode, operator);
            return true;
        }
        ruleEngine.register(ScriptRule.from(def));
        log.info("[LiteRule-Script] 脚本规则 {} 热刷新完成, operator={}", ruleCode, operator);
        return true;
    }
}
