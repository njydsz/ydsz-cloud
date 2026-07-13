package com.njydsz.pmis.literule.server.config;

import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.infra.excel.DecisionTableExcelExporter;
import com.njydsz.pmis.literule.server.impl.DecisionTableRule;
import com.njydsz.pmis.literule.server.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.server.spi.RuleConfigBroadcaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.njydsz.pmis.literule.api.RuleResult;

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
    /** Excel 导入导出器（懒加载，避免 POI 不在 classpath 时初始化失败） */
    private DecisionTableExcelExporter excelExporter;

    public DecisionTableAdminService(RuleEngine ruleEngine,
                                     DecisionTableConfigProvider configProvider,
                                     ApplicationEventPublisher eventPublisher) {
        this.ruleEngine = ruleEngine;
        this.configProvider = configProvider;
        this.eventPublisher = eventPublisher;
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
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
    public RuleResult dryRun(String tableCode,
                                                           Map<String, Object> facts) {
        DecisionTableDefinition def = configProvider.findByCode(tableCode);
        if (def == null) {
            return null;
        }
        RuleContext context =
                RuleContext.of(facts, "DRY_RUN", "MANUAL");
        DecisionTableRule rule = new DecisionTableRule(def, null);
        return rule.evaluate(context);
    }

    // ==================== Excel 导入导出（P0-3） ====================

    /**
     * 导出指定决策表为 Excel
     *
     * @param tableCode 决策表编码
     * @return xlsx 字节数组
     * @throws IllegalArgumentException 决策表不存在
     * @throws RuntimeException         导出失败
     */
    public byte[] exportExcel(String tableCode) {
        DecisionTableDefinition def = configProvider.findByCode(tableCode);
        if (def == null) {
            throw new IllegalArgumentException("决策表不存在: " + tableCode);
        }
        byte[] bytes = getExcelExporter().exportToExcel(def);
        log.info("[LiteRule-DecisionTable] 决策表已导出 Excel: code={}, bytes={}", tableCode, bytes.length);
        return bytes;
    }

    /**
     * 导入 Excel 创建/更新决策表
     *
     * @param excelBytes xlsx 字节数组
     * @param operator   操作人
     * @return 保存后的决策表定义
     * @throws IllegalArgumentException 导入失败
     */
    public DecisionTableDefinition importExcel(byte[] excelBytes, String operator) {
        DecisionTableDefinition def = getExcelExporter().importFromExcel(excelBytes);
        DecisionTableDefinition saved = save(def, operator, "Excel 导入决策表");
        log.info("[LiteRule-DecisionTable] 决策表已导入 Excel: code={}, operator={}",
                saved.getTableCode(), operator);
        return saved;
    }

    /**
     * 导出空白 Excel 模板
     *
     * @return xlsx 字节数组
     */
    public byte[] exportExcelTemplate() {
        byte[] bytes = getExcelExporter().exportTemplate();
        log.info("[LiteRule-DecisionTable] Excel 模板已导出, bytes={}", bytes.length);
        return bytes;
    }

    /**
     * 获取 Excel 导入导出器（懒加载）
     */
    private DecisionTableExcelExporter getExcelExporter() {
        if (excelExporter == null) {
            excelExporter = new DecisionTableExcelExporter();
        }
        return excelExporter;
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
            def.setRows(Collections.emptyList());
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
