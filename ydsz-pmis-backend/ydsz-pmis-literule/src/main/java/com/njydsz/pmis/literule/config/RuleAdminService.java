package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

/**
 * 规则管理服务
 *
 * <p>提供规则 CRUD、启停、版本管理、dry-run 仿真等管理操作。
 * 变更操作完成后发布 {@link RuleConfigRefreshEvent} 触发热刷新。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@RequiredArgsConstructor
public class RuleAdminService {

    private final RuleEngine ruleEngine;
    private final ExpressionEvaluator evaluator;
    private final RuleConfigProvider configProvider;
    private final RuleVersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 查询全部规则定义
     *
     * @return 全部规则定义
     */
    public List<RuleDefinition> listAll() {
        return configProvider.loadAllRules();
    }

    /**
     * 查询单条规则定义
     *
     * @param ruleCode 规则编码
     * @return 规则定义
     */
    public RuleDefinition getByCode(String ruleCode) {
        return configProvider.findByCode(ruleCode);
    }

    /**
     * 新增/更新规则（自动保存版本快照）
     *
     * @param definition 规则定义
     * @param operator   操作人
     * @param changeDesc 变更描述
     * @return 保存后的规则定义
     */
    public RuleDefinition save(RuleDefinition definition, String operator, String changeDesc) {
        // 校验表达式语法
        if (!evaluator.validate(definition.getConditionExpression())) {
            throw new IllegalArgumentException("条件表达式语法错误: " + definition.getConditionExpression());
        }
        if (definition.getSeverityExpression() != null && !definition.getSeverityExpression().isBlank()) {
            if (!evaluator.validate(definition.getSeverityExpression())) {
                throw new IllegalArgumentException("严重度表达式语法错误: " + definition.getSeverityExpression());
            }
        }

        RuleDefinition saved = configProvider.save(definition, operator);

        // 保存版本快照
        if (versionRepository != null) {
            try {
                versionRepository.saveVersion(saved, operator, changeDesc);
            } catch (Exception e) {
                log.warn("[LiteRule] 规则版本快照保存失败: {}", e.getMessage());
            }
        }

        // 发布热刷新事件
        eventPublisher.publishEvent(RuleConfigRefreshEvent.of(
                saved.getCode(),
                definition.getVersion() > 1 ? RuleConfigRefreshEvent.ChangeType.UPDATE : RuleConfigRefreshEvent.ChangeType.CREATE,
                operator));

        log.info("[LiteRule] 规则已保存: code={}, version={}, operator={}", saved.getCode(), saved.getVersion(), operator);
        return saved;
    }

    /**
     * 切换规则启停
     *
     * @param ruleCode 规则编码
     * @param enabled  是否启用
     * @param operator 操作人
     */
    public void toggle(String ruleCode, boolean enabled, String operator) {
        configProvider.toggleEnabled(ruleCode, enabled, operator);
        eventPublisher.publishEvent(RuleConfigRefreshEvent.of(
                ruleCode, RuleConfigRefreshEvent.ChangeType.TOGGLE, operator));
        log.info("[LiteRule] 规则启停切换: code={}, enabled={}, operator={}", ruleCode, enabled, operator);
    }

    /**
     * 查询规则版本历史
     *
     * @param ruleCode 规则编码
     * @return 版本历史
     */
    public List<RuleVersion> listVersions(String ruleCode) {
        if (versionRepository == null) {
            return List.of();
        }
        return versionRepository.listVersions(ruleCode);
    }

    /**
     * 回滚到指定版本
     *
     * @param ruleCode 规则编码
     * @param version  目标版本号
     * @param operator 操作人
     * @return 回滚后的规则定义
     */
    public RuleDefinition rollback(String ruleCode, int version, String operator) {
        if (versionRepository == null) {
            throw new IllegalStateException("版本仓库未配置，不支持回滚");
        }
        RuleDefinition restored = versionRepository.rollback(ruleCode, version, operator);
        eventPublisher.publishEvent(RuleConfigRefreshEvent.of(
                ruleCode, RuleConfigRefreshEvent.ChangeType.UPDATE, operator));
        log.info("[LiteRule] 规则已回滚: code={}, version={}, operator={}", ruleCode, version, operator);
        return restored;
    }

    /**
     * Dry-run 仿真（不发布事件、不记录统计）
     *
     * @param ruleCode 规则编码（null 表示仿真全部规则）
     * @param facts    事实数据
     * @return 仿真结果列表
     */
    public List<RuleResult> dryRun(String ruleCode, Map<String, Object> facts) {
        com.njydsz.pmis.literule.api.RuleContext context =
                com.njydsz.pmis.literule.api.RuleContext.of(facts, "DRY_RUN", "MANUAL");

        if (ruleCode != null) {
            // 单条规则仿真
            RuleDefinition def = configProvider.findByCode(ruleCode);
            if (def == null) {
                return List.of();
            }
            ExpressionRule rule = new ExpressionRule(def, evaluator);
            RuleResult result = rule.evaluate(context);
            return List.of(result);
        }

        // 全部规则仿真
        return ruleEngine.dryRun(context);
    }

    /**
     * 校验表达式语法
     *
     * @param expression 表达式
     * @return true=合法
     */
    public boolean validateExpression(String expression) {
        return evaluator.validate(expression);
    }
}
