package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.constant.CacheConstants;
import com.njydsz.pmis.workflow.dmn.DmnDecisionTable;
import com.njydsz.pmis.workflow.dmn.DmnEngine;
import com.njydsz.pmis.workflow.dmn.DmnHitPolicy;
import com.njydsz.pmis.workflow.dmn.DmnInput;
import com.njydsz.pmis.workflow.dmn.DmnOutput;
import com.njydsz.pmis.workflow.dmn.DmnRule;
import com.njydsz.pmis.workflow.entity.FlowDmnTableDO;
import com.njydsz.pmis.workflow.mapper.FlowDmnTableMapper;
import com.njydsz.pmis.workflow.service.FlowDmnTableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * DMN 决策表服务实现
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 * <p>核心能力：
 * <ul>
 *   <li>{@link #save} — 新建决策表，校验 tableKey 唯一性</li>
 *   <li>{@link #update} — 更新决策表定义</li>
 *   <li>{@link #publish} — 发布决策表，状态置为 PUBLISHED 并版本号 +1</li>
 *   <li>{@link #execute} — 从 DB 加载定义，反序列化 JSON 为 DmnDecisionTable，调用 DmnEngine 执行</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowDmnTableServiceImpl implements FlowDmnTableService {

    private static final TypeReference<List<DmnInput>> INPUT_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DmnOutput>> OUTPUT_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DmnRule>> RULE_TYPE = new TypeReference<>() {};

    private final FlowDmnTableMapper dmnTableMapper;
    private final DmnEngine dmnEngine;
    private final ObjectMapper objectMapper;

    // ============================== 查询 ==============================

    @Override
    @Transactional(readOnly = true)
    public FlowDmnTableDO getById(String id) {
        if (id == null) {
            return null;
        }
        return dmnTableMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.FLOW_DMN_BY_KEY_CACHE, key = "#tableKey", unless = "#result == null")
    public FlowDmnTableDO getByKey(String tableKey) {
        if (!StringUtils.hasText(tableKey)) {
            return null;
        }
        LambdaQueryWrapper<FlowDmnTableDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowDmnTableDO::getTableKey, tableKey);
        return dmnTableMapper.selectOne(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlowDmnTableDO> page(int pageNum, int pageSize, String tableName) {
        int page = pageNum < 1 ? 1 : pageNum;
        int size = pageSize < 1 ? 20 : pageSize;
        Page<FlowDmnTableDO> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<FlowDmnTableDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tableName)) {
            wrapper.like(FlowDmnTableDO::getTableName, tableName);
        }
        wrapper.orderByDesc(FlowDmnTableDO::getId);
        return dmnTableMapper.selectPage(pageReq, wrapper);
    }

    // ============================== 新建/更新 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConstants.FLOW_DMN_BY_KEY_CACHE, allEntries = true)
    public String save(FlowDmnTableDO table) {
        if (table == null) {
            throw new IllegalArgumentException("决策表定义不能为空");
        }
        if (!StringUtils.hasText(table.getTableKey())) {
            throw new IllegalArgumentException("决策表 tableKey 不能为空");
        }
        // 校验 tableKey 唯一
        FlowDmnTableDO existing = getByKey(table.getTableKey());
        if (existing != null) {
            throw new IllegalStateException("决策表 tableKey 已存在: " + table.getTableKey());
        }
        // 初始化默认值
        if (!StringUtils.hasText(table.getHitPolicy())) {
            table.setHitPolicy("UNIQUE");
        }
        if (!StringUtils.hasText(table.getCollectOperator())) {
            table.setCollectOperator("LIST");
        }
        if (!StringUtils.hasText(table.getStatus())) {
            table.setStatus("DRAFT");
        }
        if (table.getVersion() == null) {
            table.setVersion(1);
        }
        if (table.getTenantId() == null) {
            table.setTenantId("1");
        }
        dmnTableMapper.insert(table);
        log.info("[FlowDmn] 新建决策表: id={} tableKey={} tableName={}",
                table.getId(), table.getTableKey(), table.getTableName());
        return table.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConstants.FLOW_DMN_BY_KEY_CACHE, allEntries = true)
    public void update(FlowDmnTableDO table) {
        if (table == null || table.getId() == null) {
            throw new IllegalArgumentException("决策表 id 不能为空");
        }
        dmnTableMapper.updateById(table);
        log.info("[FlowDmn] 更新决策表: id={} tableKey={}", table.getId(), table.getTableKey());
    }

    // ============================== 发布 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConstants.FLOW_DMN_BY_KEY_CACHE, allEntries = true)
    public void publish(String id) {
        if (id == null) {
            throw new IllegalArgumentException("决策表 id 不能为空");
        }
        FlowDmnTableDO table = dmnTableMapper.selectById(id);
        if (table == null) {
            throw new IllegalStateException("决策表不存在: id=" + id);
        }
        FlowDmnTableDO update = new FlowDmnTableDO();
        update.setId(id);
        update.setStatus("PUBLISHED");
        update.setVersion(table.getVersion() == null ? 1 : table.getVersion() + 1);
        dmnTableMapper.updateById(update);
        log.info("[FlowDmn] 发布决策表: id={} tableKey={} version={}",
                id, table.getTableKey(), update.getVersion());
    }

    // ============================== 执行 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String tableKey, Map<String, Object> context) {
        FlowDmnTableDO table = getByKey(tableKey);
        if (table == null) {
            throw new IllegalStateException("决策表不存在: tableKey=" + tableKey);
        }
        if (!"PUBLISHED".equals(table.getStatus())) {
            throw new IllegalStateException("决策表未发布，无法执行: tableKey=" + tableKey
                    + " status=" + table.getStatus());
        }
        DmnDecisionTable decisionTable = toDecisionTable(table);
        log.info("[FlowDmn] 执行决策表: tableKey={} hitPolicy={} ruleCount={}",
                tableKey, decisionTable.getHitPolicy(),
                decisionTable.getRules() == null ? 0 : decisionTable.getRules().size());
        return dmnEngine.execute(decisionTable, context);
    }

    // ============================== 私有方法 ==============================

    /**
     * 将持久化 DO 转换为内存运行时模型 DmnDecisionTable
     *
     * <p>反序列化 inputs_json / outputs_json / rules_json 三个 JSON 字段。
     */
    private DmnDecisionTable toDecisionTable(FlowDmnTableDO table) {
        DmnDecisionTable decisionTable = new DmnDecisionTable();
        decisionTable.setTableKey(table.getTableKey());
        decisionTable.setTableName(table.getTableName());
        decisionTable.setHitPolicy(parseHitPolicy(table.getHitPolicy()));
        decisionTable.setCollectOperator(table.getCollectOperator());
        decisionTable.setInputs(parseJson(table.getInputsJson(), INPUT_TYPE));
        decisionTable.setOutputs(parseJson(table.getOutputsJson(), OUTPUT_TYPE));
        decisionTable.setRules(parseJson(table.getRulesJson(), RULE_TYPE));
        return decisionTable;
    }

    /**
     * 解析命中策略字符串为枚举，未知值默认 UNIQUE
     */
    private DmnHitPolicy parseHitPolicy(String hitPolicy) {
        if (!StringUtils.hasText(hitPolicy)) {
            return DmnHitPolicy.UNIQUE;
        }
        try {
            return DmnHitPolicy.valueOf(hitPolicy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("[FlowDmn] 未知命中策略，使用默认 UNIQUE: hitPolicy={}", hitPolicy);
            return DmnHitPolicy.UNIQUE;
        }
    }

    /**
     * 安全解析 JSON 字符串为指定类型，空串/异常返回 null
     */
    private <T> T parseJson(String json, TypeReference<T> typeRef) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.error("[FlowDmn] JSON 反序列化失败: json={} err={}", json, e.getMessage(), e);
            throw new IllegalStateException("决策表 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
