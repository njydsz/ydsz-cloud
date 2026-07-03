package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
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
 * <p>若配置了 {@link RuleConfigBroadcaster}，变更事件将通过广播器同步到所有节点，
 * 实现分布式热加载一致性。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class RuleAdminService {

    private final RuleEngine ruleEngine;
    private final ExpressionEvaluator evaluator;
    private final RuleConfigProvider configProvider;
    private final RuleVersionRepository versionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 分布式广播器（可选，配置后支持多实例热加载一致性） */
    private RuleConfigBroadcaster broadcaster;

    /** 当前节点标识（用于广播防循环） */
    private String nodeId;

    /** 是否启用 dry-run 仿真（对应 pmis.literule.dryRunEnabled 配置） */
    private boolean dryRunEnabled = true;

    /** 规则冲突检测器（可选，1.4.0 起支持） */
    private RuleConflictDetector conflictDetector;

    /** 是否启用冲突检测（对应 pmis.literule.conflictDetectionEnabled） */
    private boolean conflictDetectionEnabled = true;

    /** ERROR 级别冲突是否阻塞保存（对应 pmis.literule.conflictDetectionBlockOnError） */
    private boolean conflictDetectionBlockOnError = true;

    /**
     * 构造规则管理服务
     *
     * @param ruleEngine      规则引擎
     * @param evaluator       表达式求值器
     * @param configProvider  规则配置提供者
     * @param versionRepository 版本仓库（可为 null）
     * @param eventPublisher  事件发布器
     */
    public RuleAdminService(RuleEngine ruleEngine, ExpressionEvaluator evaluator,
                            RuleConfigProvider configProvider, RuleVersionRepository versionRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.ruleEngine = ruleEngine;
        this.evaluator = evaluator;
        this.configProvider = configProvider;
        this.versionRepository = versionRepository;
        this.eventPublisher = eventPublisher;
        this.nodeId = java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 设置分布式广播器
     *
     * @param broadcaster 广播器实例
     * @since 1.3.0
     */
    public void setBroadcaster(RuleConfigBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /**
     * 设置节点标识
     *
     * @param nodeId 节点标识
     * @since 1.3.0
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置是否启用 dry-run 仿真
     *
     * @param dryRunEnabled 是否启用
     * @since 1.3.0
     */
    public void setDryRunEnabled(boolean dryRunEnabled) {
        this.dryRunEnabled = dryRunEnabled;
    }

    /**
     * 设置规则冲突检测器
     *
     * @param conflictDetector 冲突检测器实例
     * @since 1.4.0
     */
    public void setConflictDetector(RuleConflictDetector conflictDetector) {
        this.conflictDetector = conflictDetector;
    }

    /**
     * 设置是否启用冲突检测
     *
     * @param conflictDetectionEnabled 是否启用
     * @since 1.4.0
     */
    public void setConflictDetectionEnabled(boolean conflictDetectionEnabled) {
        this.conflictDetectionEnabled = conflictDetectionEnabled;
    }

    /**
     * 设置 ERROR 级别冲突是否阻塞保存
     *
     * @param conflictDetectionBlockOnError 是否阻塞
     * @since 1.4.0
     */
    public void setConflictDetectionBlockOnError(boolean conflictDetectionBlockOnError) {
        this.conflictDetectionBlockOnError = conflictDetectionBlockOnError;
    }

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

        // 校验生命周期状态合法性 + 状态转换合法性
        validateStatusTransition(definition);

        // 冲突检测（可选，1.4.0 起支持）
        detectConflicts(definition);

        RuleDefinition saved = configProvider.save(definition, operator);

        // 保存版本快照
        if (versionRepository != null) {
            try {
                versionRepository.saveVersion(saved, operator, changeDesc);
            } catch (Exception e) {
                log.warn("[LiteRule] 规则版本快照保存失败: {}", e.getMessage());
            }
        }

        // 发布热刷新事件（基于持久化后的 version 判断 CREATE/UPDATE）
        RuleConfigRefreshEvent.ChangeType changeType = saved.getVersion() > 1
                ? RuleConfigRefreshEvent.ChangeType.UPDATE
                : RuleConfigRefreshEvent.ChangeType.CREATE;
        publishRefreshEvent(RuleConfigRefreshEvent.of(
                saved.getCode(), changeType, operator));

        log.info("[LiteRule] 规则已保存: code={}, version={}, operator={}, broadcast={}",
                saved.getCode(), saved.getVersion(), operator, broadcaster != null);
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
        publishRefreshEvent(RuleConfigRefreshEvent.of(
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
        publishRefreshEvent(RuleConfigRefreshEvent.of(
                ruleCode, RuleConfigRefreshEvent.ChangeType.UPDATE, operator));
        log.info("[LiteRule] 规则已回滚: code={}, version={}, operator={}", ruleCode, version, operator);
        return restored;
    }

    /**
     * Dry-run 仿真（不发布事件、不记录统计）
     *
     * <p>当 {@code dryRunEnabled=false} 时抛出 {@link IllegalStateException}，
     * 消费 pmis.literule.dryRunEnabled 配置开关。
     *
     * @param ruleCode 规则编码（null 表示仿真全部规则）
     * @param facts    事实数据
     * @return 仿真结果列表
     * @throws IllegalStateException dry-run 功能被禁用
     */
    public List<RuleResult> dryRun(String ruleCode, Map<String, Object> facts) {
        if (!dryRunEnabled) {
            throw new IllegalStateException("Dry-run 功能已被禁用（pmis.literule.dryRunEnabled=false）");
        }
        RuleContext context = RuleContext.of(facts, "DRY_RUN", "MANUAL");

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

    /**
     * 校验规则状态值合法性 + 状态转换合法性
     *
     * <p>规则：
     * <ul>
     *   <li>status 为空：跳过校验（由数据库默认值生效，向后兼容）</li>
     *   <li>status 非法值（无法 fromCode 解析）：抛 IllegalArgumentException</li>
     *   <li>新建（数据库中不存在该 code）：限制初始状态只能为 DRAFT 或 PUBLISHED</li>
     *   <li>更新（数据库中已存在）：校验 {@code current.canTransitionTo(target)}，
     *       状态未变化时放行</li>
     * </ul>
     *
     * @param definition 待保存的规则定义
     * @since 1.4.0
     */
    private void validateStatusTransition(RuleDefinition definition) {
        String statusStr = definition.getStatus();
        if (statusStr == null || statusStr.isBlank()) {
            return;
        }
        RuleStatus target = RuleStatus.fromCode(statusStr);
        if (target == null) {
            throw new IllegalArgumentException("非法的规则状态: " + statusStr
                    + "，合法值: DRAFT/REVIEW/PUBLISHED/DISABLED/ARCHIVED");
        }

        RuleDefinition existing = configProvider.findByCode(definition.getCode());
        if (existing == null) {
            // 新建：限制初始状态白名单（禁止 REVIEW/DISABLED/ARCHIVED 作为初始状态）
            if (target != RuleStatus.DRAFT && target != RuleStatus.PUBLISHED) {
                throw new IllegalStateException(
                        "新建规则的初始状态只能为 DRAFT 或 PUBLISHED，禁止: " + target.getDesc());
            }
            return;
        }

        // 更新：校验状态转换合法性（状态未变化时直接放行）
        RuleStatus current = parseStatusSafely(existing.getStatus());
        if (target != current && !current.canTransitionTo(target)) {
            throw new IllegalStateException("不允许的状态转换: "
                    + current.getDesc() + " -> " + target.getDesc()
                    + "（合法转换路径见 RuleStatus#canTransitionTo）");
        }
    }

    /**
     * 安全解析状态字符串，异常时回退到 PUBLISHED（数据库默认值）
     *
     * @param status 状态字符串
     * @return RuleStatus；无法解析时返回 PUBLISHED
     */
    private RuleStatus parseStatusSafely(String status) {
        RuleStatus parsed = RuleStatus.fromCode(status);
        return parsed != null ? parsed : RuleStatus.PUBLISHED;
    }

    /**
     * 执行规则冲突检测
     *
     * <p>根据配置决定是否启用、ERROR 级别冲突是否阻塞保存。
     * WARN 级别冲突仅记录日志。
     *
     * @param definition 待保存的规则定义
     * @since 1.4.0
     */
    private void detectConflicts(RuleDefinition definition) {
        if (!conflictDetectionEnabled || conflictDetector == null) {
            return;
        }
        List<RuleConflict> conflicts;
        try {
            conflicts = conflictDetector.detect(definition);
        } catch (Exception e) {
            log.warn("[LiteRule-Conflict] 冲突检测执行异常，跳过: {}", e.getMessage());
            return;
        }
        if (conflicts == null || conflicts.isEmpty()) {
            return;
        }

        boolean hasError = false;
        for (RuleConflict c : conflicts) {
            if (c.getLevel() == RuleConflict.Level.ERROR) {
                hasError = true;
                log.error("[LiteRule-Conflict] {} 冲突: {} vs {} - {}",
                        c.getType(), c.getNewRuleCode(), c.getConflictingRuleCode(), c.getDescription());
            } else {
                log.warn("[LiteRule-Conflict] {} 提示: {} vs {} - {}",
                        c.getType(), c.getNewRuleCode(), c.getConflictingRuleCode(), c.getDescription());
            }
        }

        if (hasError && conflictDetectionBlockOnError) {
            RuleConflict firstError = conflicts.stream()
                    .filter(c -> c.getLevel() == RuleConflict.Level.ERROR)
                    .findFirst().orElse(null);
            throw new IllegalStateException("规则冲突检测未通过（"
                    + conflicts.size() + " 项冲突，其中 "
                    + conflicts.stream().filter(c -> c.getLevel() == RuleConflict.Level.ERROR).count()
                    + " 项 ERROR）: " + (firstError != null ? firstError.getDescription() : ""));
        }
    }

    /**
     * 发布规则刷新事件（本地 + 分布式广播）
     *
     * <p>先发布本地 Spring 事件触发热加载，再通过广播器通知其他节点。
     * 广播器不可用时仅本地生效（向后兼容）。
     *
     * @param event 规则变更事件
     * @since 1.3.0
     */
    private void publishRefreshEvent(RuleConfigRefreshEvent event) {
        // 1. 本地事件（当前节点热加载）
        eventPublisher.publishEvent(event);
        // 2. 分布式广播（其他节点热加载）
        if (broadcaster != null && broadcaster.isAvailable()) {
            try {
                broadcaster.broadcast(event, nodeId);
            } catch (Exception e) {
                log.warn("[LiteRule] 分布式广播失败，仅当前节点已刷新: {}", e.getMessage());
            }
        }
    }
}
