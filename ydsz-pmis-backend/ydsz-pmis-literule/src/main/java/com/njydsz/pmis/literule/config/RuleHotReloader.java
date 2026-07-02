package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
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

    /**
     * 启动时全量加载规则
     */
    @PostConstruct
    public void initLoad() {
        if (!properties.isHotReloadEnabled()) {
            log.info("[LiteRule] 热加载已禁用，跳过初始加载");
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
            List<RuleDefinition> definitions = configProvider.loadEnabledRules();
            // 先注销所有表达式规则（保留编程式注册的 StaticRule）
            for (Rule existing : ruleEngine.getRules()) {
                if (existing instanceof ExpressionRule) {
                    ruleEngine.unregister(existing.getCode());
                }
            }
            // 重新注册
            int count = 0;
            for (RuleDefinition def : definitions) {
                if (!def.isEnabled()) continue;
                try {
                    ExpressionRule rule = new ExpressionRule(def, evaluator);
                    ruleEngine.register(rule);
                    count++;
                } catch (Exception e) {
                    log.warn("[LiteRule] 规则 {} 加载失败: {}", def.getCode(), e.getMessage());
                }
            }
            log.info("[LiteRule] 全量热刷新完成: 加载 {} 条规则, operator={}", count, operator);
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
            case DELETE -> ruleEngine.unregister(event.getRuleCode());
            default -> reloadSingle(event.getRuleCode(), event.getOperator());
        }
    }

    /**
     * 重新加载单条规则
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     */
    private void reloadSingle(String ruleCode, String operator) {
        try {
            RuleDefinition def = configProvider.findByCode(ruleCode);
            if (def == null || !def.isEnabled()) {
                ruleEngine.unregister(ruleCode);
                log.info("[LiteRule] 规则 {} 已注销（未找到或已禁用）, operator={}", ruleCode, operator);
                return;
            }
            ExpressionRule rule = new ExpressionRule(def, evaluator);
            ruleEngine.register(rule);
            log.info("[LiteRule] 规则 {} 热刷新完成, operator={}", ruleCode, operator);
        } catch (Exception e) {
            log.error("[LiteRule] 规则 {} 热刷新失败: {}", ruleCode, e.getMessage(), e);
        }
    }
}
