package com.njydsz.pmis.project.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.literule.api.DecisionTableDefinition;
import com.njydsz.pmis.literule.api.HitPolicy;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.project.entity.DecisionTableDO;
import com.njydsz.pmis.project.mapper.DecisionTableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 决策表配置提供者实现（SPI 桥接 literule ↔ project）
 *
 * <p>P0-2: 将 {@link DecisionTableDO}（持久化实体）适配为 {@link DecisionTableDefinition}（literule 引擎 POJO），
 * 让 literule 模块的 {@code DecisionTableAdminService} 和 {@code RuleHotReloader} 自动生效，
 * 实现 DMN 决策表的热加载、CRUD 管理与 RuleEngine 内嵌评估。
 *
 * <p>本实现注册后，{@code dmn:} 前缀路由分发的两条路径均可工作：
 * <ol>
 *   <li>project 模块路径：{@code FlowRoutingServiceImpl} → {@code DecisionTableEvalService} → {@code DecisionTableEvaluator}</li>
 *   <li>literule 模块路径：{@code RuleHotReloader} 加载决策表到 {@code RuleEngine} → {@code DecisionTableRule}</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DecisionTableConfigProviderImpl implements DecisionTableConfigProvider {

    private final DecisionTableMapper decisionTableMapper;

    @Override
    public List<DecisionTableDefinition> loadEnabledTables() {
        List<DecisionTableDO> list = decisionTableMapper.selectList(
                new LambdaQueryWrapper<DecisionTableDO>()
                        .eq(DecisionTableDO::getEnabled, true)
                        .orderByAsc(DecisionTableDO::getPriority));
        return list.stream().map(this::toDefinition).collect(Collectors.toList());
    }

    @Override
    public List<DecisionTableDefinition> loadAllTables() {
        List<DecisionTableDO> list = decisionTableMapper.selectList(
                new LambdaQueryWrapper<DecisionTableDO>()
                        .orderByAsc(DecisionTableDO::getPriority));
        return list.stream().map(this::toDefinition).collect(Collectors.toList());
    }

    @Override
    public DecisionTableDefinition save(DecisionTableDefinition definition, String operator) {
        DecisionTableDO existing = findByCodeDo(definition.getTableCode());
        DecisionTableDO entity = toDO(definition, existing);
        if (entity.getUpdatedBy() == null) {
            entity.setUpdatedBy(operator);
            entity.setUpdatedAt(LocalDateTime.now());
        }
        if (existing == null) {
            entity.setCreatedBy(operator);
            entity.setCreatedAt(LocalDateTime.now());
            decisionTableMapper.insert(entity);
            log.info("[DMN] 决策表已创建: code={} version={}", definition.getTableCode(), entity.getVersion());
        } else {
            entity.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
            decisionTableMapper.updateById(entity);
            log.info("[DMN] 决策表已更新: code={} version={}", definition.getTableCode(), entity.getVersion());
        }
        definition.setVersion(entity.getVersion());
        return definition;
    }

    @Override
    public void toggleEnabled(String tableCode, boolean enabled, String operator) {
        decisionTableMapper.update(null,
                new LambdaUpdateWrapper<DecisionTableDO>()
                        .eq(DecisionTableDO::getTableCode, tableCode)
                        .set(DecisionTableDO::getEnabled, enabled)
                        .set(DecisionTableDO::getUpdatedBy, operator)
                        .set(DecisionTableDO::getUpdatedAt, LocalDateTime.now()));
        log.info("[DMN] 决策表启停切换: code={} enabled={}", tableCode, enabled);
    }

    @Override
    public DecisionTableDefinition findByCode(String tableCode) {
        DecisionTableDO entity = findByCodeDo(tableCode);
        return entity == null ? null : toDefinition(entity);
    }

    @Override
    public void delete(String tableCode, String operator) {
        decisionTableMapper.delete(
                new LambdaQueryWrapper<DecisionTableDO>()
                        .eq(DecisionTableDO::getTableCode, tableCode));
        log.info("[DMN] 决策表已删除: code={} operator={}", tableCode, operator);
    }

    // ============================== 类型转换 ==============================

    private DecisionTableDO findByCodeDo(String tableCode) {
        return decisionTableMapper.selectOne(
                new LambdaQueryWrapper<DecisionTableDO>()
                        .eq(DecisionTableDO::getTableCode, tableCode));
    }

    /**
     * DecisionTableDO → DecisionTableDefinition
     */
    @SuppressWarnings("unchecked")
    private DecisionTableDefinition toDefinition(DecisionTableDO entity) {
        return DecisionTableDefinition.builder()
                .tableCode(entity.getTableCode())
                .tableName(entity.getTableName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .hitPolicy(parseHitPolicy(entity.getHitPolicy()))
                .conditionColumns(convertToColumns(entity.getConditionColumns()))
                .actionColumns(convertToColumns(entity.getActionColumns()))
                .rows(convertToRows(entity.getRows()))
                .defaultActions(entity.getDefaultActions())
                .enabled(entity.getEnabled() == null || entity.getEnabled())
                .priority(entity.getPriority() == null ? 100 : entity.getPriority())
                .version(entity.getVersion() == null ? 1 : entity.getVersion())
                .build();
    }

    /**
     * DecisionTableDefinition → DecisionTableDO（用于 save）
     */
    private DecisionTableDO toDO(DecisionTableDefinition def, DecisionTableDO existing) {
        DecisionTableDO entity = existing != null ? existing : new DecisionTableDO();
        entity.setTableCode(def.getTableCode());
        entity.setTableName(def.getTableName());
        entity.setDescription(def.getDescription());
        entity.setCategory(def.getCategory());
        entity.setHitPolicy(def.getHitPolicy() == null ? HitPolicy.FIRST.name() : def.getHitPolicy().name());
        entity.setConditionColumns(convertColumnsToMaps(def.getConditionColumns()));
        entity.setActionColumns(convertColumnsToMaps(def.getActionColumns()));
        entity.setRows(convertRowsToMaps(def.getRows()));
        entity.setDefaultActions(def.getDefaultActions());
        entity.setEnabled(def.isEnabled());
        entity.setPriority(def.getPriority());
        if (existing == null) {
            entity.setVersion(1);
        }
        return entity;
    }

    @SuppressWarnings("unchecked")
    private List<DecisionTableDefinition.Column> convertToColumns(List<Map<String, Object>> columns) {
        if (columns == null) return new ArrayList<>();
        List<DecisionTableDefinition.Column> result = new ArrayList<>(columns.size());
        for (Map<String, Object> col : columns) {
            result.add(DecisionTableDefinition.Column.builder()
                    .name((String) col.get("name"))
                    .label((String) col.get("label"))
                    .type((String) col.get("type"))
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertColumnsToMaps(List<DecisionTableDefinition.Column> columns) {
        if (columns == null) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>(columns.size());
        for (DecisionTableDefinition.Column col : columns) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", col.getName());
            map.put("label", col.getLabel());
            map.put("type", col.getType());
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<DecisionTableDefinition.Row> convertToRows(List<Map<String, Object>> rows) {
        if (rows == null) return new ArrayList<>();
        List<DecisionTableDefinition.Row> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, String> conditions = (Map<String, String>) row.get("conditions");
            Map<String, Object> actions = (Map<String, Object>) row.get("actions");
            Object priorityVal = row.get("priority");
            int priority = priorityVal instanceof Number n ? n.intValue() : 100;
            result.add(DecisionTableDefinition.Row.builder()
                    .conditions(conditions)
                    .actions(actions)
                    .priority(priority)
                    .build());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertRowsToMaps(List<DecisionTableDefinition.Row> rows) {
        if (rows == null) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (DecisionTableDefinition.Row row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("conditions", row.getConditions());
            map.put("actions", row.getActions());
            map.put("priority", row.getPriority());
            result.add(map);
        }
        return result;
    }

    private HitPolicy parseHitPolicy(String hitPolicy) {
        if (hitPolicy == null || hitPolicy.isBlank()) {
            return HitPolicy.FIRST;
        }
        try {
            return HitPolicy.valueOf(hitPolicy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[DMN] 未知的 hitPolicy '{}'，回退到 FIRST", hitPolicy);
            return HitPolicy.FIRST;
        }
    }
}
