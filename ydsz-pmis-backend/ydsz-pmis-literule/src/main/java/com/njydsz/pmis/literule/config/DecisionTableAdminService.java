package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.impl.DecisionTableRule;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

/**
 * 决策表管理服务
 *
 * <p>提供决策表 CRUD、启停、dry-run、热刷新等管理操作。
 * 与 {@link RuleAdminService} 解耦，可独立启用。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
public class DecisionTableAdminService {

    private final RuleEngine ruleEngine;
    private final DecisionTableConfigProvider configProvider;
    private final ApplicationEventPublisher eventPublisher;
    private RuleConfigBroadcaster broadcaster;
    private String nodeId;

    public DecisionTableAdminService(RuleEngine ruleEngine,
                                     DecisionTableConfigProvider configProvider,
                                     ApplicationEventPublisher eventPublisher) {
        this.ruleEngine = ruleEngine;
        this.configProvider = configProvider;
        this.eventPublisher = eventPublisher;
        this.nodeId = java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    public void setBroadcaster(RuleConfigBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 查询全部决策表
     */
    public List<DecisionTableDefinition> listAll() {
        return configProvider.loadAllTables();
    }

    /**
     * 根据编码查询
     */
    public DecisionTableDefinition getByCode(String tableCode) {
        return configProvider.findByCode(tableCode);
    }

    /**
     * 新增/更新决策表
     */
    public DecisionTableDefinition save(DecisionTableDefinition definition, String operator, String changeDesc) {
        validate(definition);
        DecisionTableDefinition saved = configProvider.save(definition, operator);
        publishRefreshEvent(RuleConfigRefreshEvent.of(
                saved.getTableCode(), RuleConfigRefreshEvent.ChangeType.UPDATE, operator));
        log.info("[LiteRule-DecisionTable] 决策表已保存: code={}, version={}, operator={}",
                saved.getTableCode(), saved.getVersion(), operator);
        return saved;
    }

    /**
     * 切换启停
     */
    public void toggle(String tableCode, boolean enabled, String operator) {
        configProvider.toggleEnabled(tableCode, enabled, operator);
        publishRefreshEvent(RuleConfigRefreshEvent.of(
                tableCode, RuleConfigRefreshEvent.ChangeType.TOGGLE, operator));
        log.info("[LiteRule-DecisionTable] 决策表启停切换: code={}, enabled={}, operator={}",
                tableCode, enabled, operator);
    }

    /**
     * 删除决策表
     */
    public void delete(String tableCode, String operator) {
        configProvider.delete(tableCode, operator);
        ruleEngine.unregister(tableCode);
        publishRefreshEvent(RuleConfigRefreshEvent.of(
                tableCode, RuleConfigRefreshEvent.ChangeType.DELETE, operator));
        log.info("[LiteRule-DecisionTable] 决策表已删除: code={}, operator={}", tableCode, operator);
    }

    /**
     * dry-run：构建临时 DecisionTableRule 评估（不注册到引擎）
     */
    public com.njydsz.pmis.literule.api.RuleResult dryRun(String tableCode,
                                                           java.util.Map<String, Object> facts) {
        DecisionTableDefinition def = configProvider.findByCode(tableCode);
        if (def == null) {
            return null;
        }
        com.njydsz.pmis.literule.api.RuleContext context =
                com.njydsz.pmis.literule.api.RuleContext.of(facts, "DRY_RUN", "MANUAL");
        DecisionTableRule rule = new DecisionTableRule(def, null);
        return rule.evaluate(context);
    }

    private void validate(DecisionTableDefinition def) {
        if (def.getTableCode() == null || def.getTableCode().isBlank()) {
            throw new IllegalArgumentException("决策表编码 tableCode 不能为空");
        }
        if (def.getTableName() == null || def.getTableName().isBlank()) {
            throw new IllegalArgumentException("决策表名称 tableName 不能为空");
        }
        if (def.getConditionColumns() == null || def.getConditionColumns().isEmpty()) {
            throw new IllegalArgumentException("决策表条件列 conditionColumns 不能为空");
        }
        if (def.getActionColumns() == null || def.getActionColumns().isEmpty()) {
            throw new IllegalArgumentException("决策表动作列 actionColumns 不能为空");
        }
        if (def.getRows() == null) {
            def.setRows(java.util.Collections.emptyList());
        }
        if (def.getHitPolicy() == null) {
            def.setHitPolicy(HitPolicy.FIRST);
        }
    }

    private void publishRefreshEvent(RuleConfigRefreshEvent event) {
        eventPublisher.publishEvent(event);
        if (broadcaster != null && broadcaster.isAvailable()) {
            try {
                broadcaster.broadcast(event, nodeId);
            } catch (Exception e) {
                log.warn("[LiteRule-DecisionTable] 分布式广播失败: {}", e.getMessage());
            }
        }
    }
}
