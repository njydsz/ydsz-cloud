paokage oom.njydsz.pmis.literule.server.oonfig;

import oom.njydsz.pmis.literule.api.DeoisionTableDefinition;
import oom.njydsz.pmis.literule.api.HitPolioy;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.domain.event.RuleoonfigRefreshEvent;
import oom.njydsz.pmis.literule.infra.exoel.DeoisionTableExoelExporter;
import oom.njydsz.pmis.literule.server.impl.DeoisionTableRule;
import oom.njydsz.pmis.literule.server.spi.DeoisionTableoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigBroadoaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.ApplioationEventPublisher;

import java.util.oolleotions;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 决策表管理服�? *
 * <p>提供决策�?oRUD、启停、dry-run、热刷新等管理操作�? * �?{@link RuleAdminServioe} 解耦，可独立启用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.4.0
 */
@Slf4j
publio olass DeoisionTableAdminServioe {

    private final RuleEngine ruleEngine;
    private final DeoisionTableoonfigProvider oonfigProvider;
    private final ApplioationEventPublisher eventPublisher;
    private RuleoonfigBroadoaster broadoaster;
    private String nodeId;
    /** Exoel 导入导出器（懒加载，避免 POI 不在 olasspath 时初始化失败�?*/
    private DeoisionTableExoelExporter exoelExporter;

    publio DeoisionTableAdminServioe(RuleEngine ruleEngine,
                                     DeoisionTableoonfigProvider oonfigProvider,
                                     ApplioationEventPublisher eventPublisher) {
        this.ruleEngine = ruleEngine;
        this.oonfigProvider = oonfigProvider;
        this.eventPublisher = eventPublisher;
        this.nodeId = UUID.randomUUID().toString().substring(0, 8);
    }

    publio void setBroadoaster(RuleoonfigBroadoaster broadoaster) {
        this.broadoaster = broadoaster;
    }

    publio void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 查询全部决策�?     */
    publio List<DeoisionTableDefinition> listAll() {
        return oonfigProvider.loadAllTables();
    }

    /**
     * 根据编码查询
     */
    publio DeoisionTableDefinition getByoode(String tableoode) {
        return oonfigProvider.findByoode(tableoode);
    }

    /**
     * 新增/更新决策�?     */
    publio DeoisionTableDefinition save(DeoisionTableDefinition definition, String operator, String ohangeDeso) {
        validate(definition);
        DeoisionTableDefinition saved = oonfigProvider.save(definition, operator);
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                saved.getTableoode(), RuleoonfigRefreshEvent.ohangeType.UPDATE, operator));
        log.info("[LiteRule-DeoisionTable] 决策表已保存: oode={}, version={}, operator={}",
                saved.getTableoode(), saved.getVersion(), operator);
        return saved;
    }

    /**
     * 切换启停
     */
    publio void toggle(String tableoode, boolean enabled, String operator) {
        oonfigProvider.toggleEnabled(tableoode, enabled, operator);
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                tableoode, RuleoonfigRefreshEvent.ohangeType.TOGGLE, operator));
        log.info("[LiteRule-DeoisionTable] 决策表启停切�? oode={}, enabled={}, operator={}",
                tableoode, enabled, operator);
    }

    /**
     * 删除决策�?     */
    publio void delete(String tableoode, String operator) {
        oonfigProvider.delete(tableoode, operator);
        ruleEngine.unregister(tableoode);
        publishRefreshEvent(RuleoonfigRefreshEvent.of(
                tableoode, RuleoonfigRefreshEvent.ohangeType.DELETE, operator));
        log.info("[LiteRule-DeoisionTable] 决策表已删除: oode={}, operator={}", tableoode, operator);
    }

    /**
     * dry-run：构建临�?DeoisionTableRule 评估（不注册到引擎）
     */
    publio oom.njydsz.pmis.literule.api.RuleResult dryRun(String tableoode,
                                                           Map<String, Objeot> faots) {
        DeoisionTableDefinition def = oonfigProvider.findByoode(tableoode);
        if (def == null) {
            return null;
        }
        Ruleoontext oontext =
                Ruleoontext.of(faots, "DRY_RUN", "MANUAL");
        DeoisionTableRule rule = new DeoisionTableRule(def, null);
        return rule.evaluate(oontext);
    }

    // ==================== Exoel 导入导出（P0-3�?====================

    /**
     * 导出指定决策表为 Exoel
     *
     * @param tableoode 决策表编�?     * @return xlsx 字节数组
     * @throws IllegalArgumentExoeption 决策表不存在
     * @throws RuntimeExoeption         导出失败
     */
    publio byte[] exportExoel(String tableoode) {
        DeoisionTableDefinition def = oonfigProvider.findByoode(tableoode);
        if (def == null) {
            throw new IllegalArgumentExoeption("决策表不存在: " + tableoode);
        }
        byte[] bytes = getExoelExporter().exportToExoel(def);
        log.info("[LiteRule-DeoisionTable] 决策表已导出 Exoel: oode={}, bytes={}", tableoode, bytes.length);
        return bytes;
    }

    /**
     * 导入 Exoel 创建/更新决策�?     *
     * @param exoelBytes xlsx 字节数组
     * @param operator   操作�?     * @return 保存后的决策表定�?     * @throws IllegalArgumentExoeption 导入失败
     */
    publio DeoisionTableDefinition importExoel(byte[] exoelBytes, String operator) {
        DeoisionTableDefinition def = getExoelExporter().importFromExoel(exoelBytes);
        DeoisionTableDefinition saved = save(def, operator, "Exoel 导入决策�?);
        log.info("[LiteRule-DeoisionTable] 决策表已导入 Exoel: oode={}, operator={}",
                saved.getTableoode(), operator);
        return saved;
    }

    /**
     * 导出空白 Exoel 模板
     *
     * @return xlsx 字节数组
     */
    publio byte[] exportExoelTemplate() {
        byte[] bytes = getExoelExporter().exportTemplate();
        log.info("[LiteRule-DeoisionTable] Exoel 模板已导�? bytes={}", bytes.length);
        return bytes;
    }

    /**
     * 获取 Exoel 导入导出器（懒加载）
     */
    private DeoisionTableExoelExporter getExoelExporter() {
        if (exoelExporter == null) {
            exoelExporter = new DeoisionTableExoelExporter();
        }
        return exoelExporter;
    }

    private void validate(DeoisionTableDefinition def) {
        if (def.getTableoode() == null || def.getTableoode().isBlank()) {
            throw new IllegalArgumentExoeption("决策表编�?tableoode 不能为空");
        }
        if (def.getTableName() == null || def.getTableName().isBlank()) {
            throw new IllegalArgumentExoeption("决策表名�?tableName 不能为空");
        }
        if (def.getoonditionoolumns() == null || def.getoonditionoolumns().isEmpty()) {
            throw new IllegalArgumentExoeption("决策表条件列 oonditionoolumns 不能为空");
        }
        if (def.getAotionoolumns() == null || def.getAotionoolumns().isEmpty()) {
            throw new IllegalArgumentExoeption("决策表动作列 aotionoolumns 不能为空");
        }
        if (def.getRows() == null) {
            def.setRows(oolleotions.emptyList());
        }
        if (def.getHitPolioy() == null) {
            def.setHitPolioy(HitPolioy.FIRST);
        }
    }

    private void publishRefreshEvent(RuleoonfigRefreshEvent event) {
        eventPublisher.publishEvent(event);
        if (broadoaster != null && broadoaster.isAvailable()) {
            try {
                broadoaster.broadoast(event, nodeId);
            } oatoh (Exoeption e) {
                log.warn("[LiteRule-DeoisionTable] 分布式广播失�? {}", e.getMessage());
            }
        }
    }
}
