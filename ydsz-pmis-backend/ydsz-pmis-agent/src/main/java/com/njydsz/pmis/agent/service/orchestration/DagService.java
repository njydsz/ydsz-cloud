package com.njydsz.pmis.agent.service.orchestration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.entity.orchestration.DagDefinitionDO;
import com.njydsz.pmis.agent.entity.orchestration.DagInstanceDO;
import com.njydsz.pmis.agent.entity.orchestration.DagNodeInstanceDO;
import com.njydsz.pmis.agent.mapper.orchestration.DagDefinitionMapper;
import com.njydsz.pmis.agent.mapper.orchestration.DagInstanceMapper;
import com.njydsz.pmis.agent.mapper.orchestration.DagNodeInstanceMapper;
import com.njydsz.pmis.agent.orchestration.dag.DagDefinition;
import com.njydsz.pmis.agent.orchestration.dag.DagExecutionResult;
import com.njydsz.pmis.agent.orchestration.dag.DagExecutor;
import com.njydsz.pmis.agent.orchestration.dag.DagNodeStatus;
import com.njydsz.pmis.agent.service.agent.ValidationResult;
import com.njydsz.pmis.common.api.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAG 编排服务（P3-2 落地）。
 *
 * <p>封装 DAG 定义 CRUD、执行、历史查询。
 * 使用 {@link ObjectProvider} 注入 Mapper / Executor / Agent，避免无 DB 环境启动失败。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Slf4j
@Service
public class DagService {

    private final ObjectProvider<DagDefinitionMapper> defMapperProvider;
    private final ObjectProvider<DagInstanceMapper> instMapperProvider;
    private final ObjectProvider<DagNodeInstanceMapper> nodeMapperProvider;
    private final ObjectProvider<DagExecutor> executorProvider;
    private final ObjectProvider<List<Agent>> agentsProvider;
    private final ObjectMapper objectMapper;

    public DagService(ObjectProvider<DagDefinitionMapper> defMapperProvider,
                      ObjectProvider<DagInstanceMapper> instMapperProvider,
                      ObjectProvider<DagNodeInstanceMapper> nodeMapperProvider,
                      ObjectProvider<DagExecutor> executorProvider,
                      ObjectProvider<List<Agent>> agentsProvider,
                      ObjectMapper objectMapper) {
        this.defMapperProvider = defMapperProvider;
        this.instMapperProvider = instMapperProvider;
        this.nodeMapperProvider = nodeMapperProvider;
        this.executorProvider = executorProvider;
        this.agentsProvider = agentsProvider;
        this.objectMapper = objectMapper;
    }

    // ==================== DAG 定义管理 ====================

    /**
     * 创建 DAG 定义。
     *
     * @param dag DAG 定义（含节点列表）
     * @return 持久化后的 DO
     */
    public DagDefinitionDO createDefinition(DagDefinition dag) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("DagDefinitionMapper 不可用");
        }
        DagDefinitionDO def = new DagDefinitionDO();
        def.setTenantId(dag.getTenantId() != null ? dag.getTenantId() : "1");
        def.setName(dag.getName());
        def.setDescription(dag.getDescription());
        def.setBizType(dag.getBizType());
        def.setVersion(dag.getVersion() != null ? dag.getVersion() : "1.0.0");
        def.setDefinitionJson(serialize(dag));
        def.setFailureStrategy(dag.getFailureStrategy() != null
                ? dag.getFailureStrategy().name() : "ABORT");
        def.setMaxRetries(dag.getMaxRetries() != null ? dag.getMaxRetries() : 3);
        def.setDefaultTimeoutMs(dag.getDefaultTimeoutMs());
        def.setEnabled(dag.getEnabled() == null || dag.getEnabled() ? 1 : 0);
        mapper.insert(def);
        dag.setId(def.getId());
        return def;
    }

    /**
     * 更新 DAG 定义（P1-7 落地）。
     *
     * @param id  DAG 定义 ID
     * @param dag 新的 DAG 定义内容
     * @return 更新后的 DO
     */
    public DagDefinitionDO updateDefinition(String id, DagDefinition dag) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("DagDefinitionMapper 不可用");
        }
        DagDefinitionDO existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("DAG 定义不存在: " + id);
        }
        existing.setName(dag.getName() != null ? dag.getName() : existing.getName());
        existing.setDescription(dag.getDescription() != null ? dag.getDescription() : existing.getDescription());
        existing.setBizType(dag.getBizType() != null ? dag.getBizType() : existing.getBizType());
        existing.setVersion(dag.getVersion() != null ? dag.getVersion() : existing.getVersion());
        existing.setDefinitionJson(serialize(dag));
        if (dag.getFailureStrategy() != null) {
            existing.setFailureStrategy(dag.getFailureStrategy().name());
        }
        if (dag.getMaxRetries() != null) {
            existing.setMaxRetries(dag.getMaxRetries());
        }
        if (dag.getDefaultTimeoutMs() != 0) {
            existing.setDefaultTimeoutMs(dag.getDefaultTimeoutMs());
        }
        if (dag.getEnabled() != null) {
            existing.setEnabled(dag.getEnabled() ? 1 : 0);
        }
        mapper.updateById(existing);
        log.info("[DAG] 更新定义: id={}, name={}", id, existing.getName());
        return existing;
    }

    /**
     * 删除 DAG 定义（软删除，P1-7 落地）。
     *
     * @param id DAG 定义 ID
     */
    public void deleteDefinition(String id) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("DagDefinitionMapper 不可用");
        }
        DagDefinitionDO existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("DAG 定义不存在: " + id);
        }
        mapper.deleteById(id);
        log.info("[DAG] 删除定义: id={}, name={}", id, existing.getName());
    }

    /**
     * 启用/禁用 DAG 定义（P1-7 落地）。
     *
     * @param id      DAG 定义 ID
     * @param enabled 是否启用
     * @return 更新后的 DO
     */
    public DagDefinitionDO toggleEnabled(String id, boolean enabled) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            throw new IllegalStateException("DagDefinitionMapper 不可用");
        }
        DagDefinitionDO existing = mapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("DAG 定义不存在: " + id);
        }
        existing.setEnabled(enabled ? 1 : 0);
        mapper.updateById(existing);
        log.info("[DAG] {} 定义: id={}", enabled ? "启用" : "禁用", id);
        return existing;
    }

    /**
     * 验证 DAG 定义结构（P1-7 落地）。
     *
     * <p>检查项：
     * <ul>
     *   <li>节点列表非空</li>
     *   <li>节点名唯一</li>
     *   <li>依赖引用的节点存在</li>
     *   <li>无循环依赖（环检测）</li>
     *   <li>至少有一个起始节点（无依赖的节点）</li>
     * </ul>
     *
     * @param dag DAG 定义
     * @return 验证结果（valid=true 表示通过）
     */
    public ValidationResult validateDefinition(DagDefinition dag) {
        if (dag == null || dag.getNodes() == null || dag.getNodes().isEmpty()) {
            return ValidationResult.failure("DAG 节点列表为空");
        }

        List<String> errors = new ArrayList<>();

        // 1. 节点名唯一性检查
        Set<String> nodeNames = new HashSet<>();
        for (var node : dag.getNodes()) {
            if (node.getName() == null || node.getName().isBlank()) {
                errors.add("存在未命名的节点");
                continue;
            }
            if (!nodeNames.add(node.getName())) {
                errors.add("节点名重复: " + node.getName());
            }
        }

        // 2. 依赖引用检查
        for (var node : dag.getNodes()) {
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    if (!nodeNames.contains(dep)) {
                        errors.add("节点 [" + node.getName() + "] 依赖了不存在的节点: " + dep);
                    }
                }
            }
        }

        // 3. 环检测（DFS）
        if (errors.isEmpty()) {
            Set<String> visiting = new HashSet<>();
            Set<String> visited = new HashSet<>();
            for (var node : dag.getNodes()) {
                if (hasCycle(node.getName(), dag, visiting, visited)) {
                    errors.add("DAG 存在循环依赖");
                    break;
                }
            }
        }

        // 4. 起始节点检查
        boolean hasStartNode = dag.getNodes().stream()
                .anyMatch(n -> n.getDependsOn() == null || n.getDependsOn().isEmpty());
        if (!hasStartNode) {
            errors.add("DAG 缺少起始节点（无依赖的节点）");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * DFS 环检测。
     */
    private boolean hasCycle(String nodeName, DagDefinition dag,
                              Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeName)) return false;
        if (visiting.contains(nodeName)) return true;

        visiting.add(nodeName);
        var node = dag.findNode(nodeName);
        if (node != null && node.getDependsOn() != null) {
            for (String dep : node.getDependsOn()) {
                if (hasCycle(dep, dag, visiting, visited)) {
                    return true;
                }
            }
        }
        visiting.remove(nodeName);
        visited.add(nodeName);
        return false;
    }

    /**
     * 查询 DAG 定义详情。
     *
     * @param id DAG 定义 ID
     * @return DO；不存在返回 null
     */
    public DagDefinitionDO getDefinition(String id) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.selectById(id);
    }

    /**
     * 分页查询 DAG 定义。
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param tenantId 租户 ID（可选）
     * @return 分页结果
     */
    public PageResult<DagDefinitionDO> pageDefinitions(int pageNum, int pageSize, String tenantId) {
        DagDefinitionMapper mapper = defMapperProvider.getIfAvailable();
        if (mapper == null) {
            return PageResult.empty();
        }
        LambdaQueryWrapper<DagDefinitionDO> wrapper = new LambdaQueryWrapper<>();
        if (tenantId != null && !tenantId.isBlank()) {
            wrapper.eq(DagDefinitionDO::getTenantId, tenantId);
        }
        wrapper.orderByDesc(DagDefinitionDO::getCreatedAt);
        Page<DagDefinitionDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    // ==================== DAG 执行 ====================

    /**
     * 执行 DAG。
     *
     * @param definitionId DAG 定义 ID
     * @param globalInputs 全局输入参数
     * @return 执行结果
     */
    public DagExecutionResult execute(String definitionId, Map<String, Object> globalInputs) {
        // 1. 读取 DAG 定义
        DagDefinitionDO defDO = getDefinition(definitionId);
        if (defDO == null) {
            throw new IllegalStateException("DAG 定义不存在: " + definitionId);
        }
        DagDefinition dag = deserialize(defDO.getDefinitionJson());
        dag.setId(defDO.getId());
        dag.setTenantId(defDO.getTenantId());

        // 2. 收集 Agent
        Map<String, Agent> agents = collectAgents();

        // 3. 执行
        DagExecutor executor = executorProvider.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException("DagExecutor 不可用");
        }
        DagExecutionResult result = executor.execute(dag, agents, globalInputs, null);

        // 4. 持久化执行实例
        persistResult(defDO, result, globalInputs);

        return result;
    }

    /**
     * 直接执行 DAG 定义（无需持久化，用于测试或临时编排）。
     *
     * @param dag          DAG 定义
     * @param globalInputs 全局输入参数
     * @return 执行结果
     */
    public DagExecutionResult executeDirect(DagDefinition dag, Map<String, Object> globalInputs) {
        DagExecutor executor = executorProvider.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException("DagExecutor 不可用");
        }
        Map<String, Agent> agents = collectAgents();
        return executor.execute(dag, agents, globalInputs, null);
    }

    // ==================== 执行历史 ====================

    /**
     * 查询 DAG 执行历史。
     *
     * @param definitionId DAG 定义 ID
     * @param pageNum      页码
     * @param pageSize     每页大小
     * @return 分页结果
     */
    public PageResult<DagInstanceDO> pageInstances(String definitionId, int pageNum, int pageSize) {
        DagInstanceMapper mapper = instMapperProvider.getIfAvailable();
        if (mapper == null) {
            return PageResult.empty();
        }
        LambdaQueryWrapper<DagInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DagInstanceDO::getDagDefinitionId, definitionId);
        wrapper.orderByDesc(DagInstanceDO::getCreatedAt);
        Page<DagInstanceDO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询 DAG 执行实例详情。
     *
     * @param instanceId 实例 ID
     * @return 实例 DO
     */
    public DagInstanceDO getInstance(String instanceId) {
        DagInstanceMapper mapper = instMapperProvider.getIfAvailable();
        if (mapper == null) {
            return null;
        }
        return mapper.selectById(instanceId);
    }

    /**
     * 查询节点执行明细。
     *
     * @param instanceId DAG 实例 ID
     * @return 节点实例列表
     */
    public List<DagNodeInstanceDO> listNodeInstances(String instanceId) {
        DagNodeInstanceMapper mapper = nodeMapperProvider.getIfAvailable();
        if (mapper == null) {
            return List.of();
        }
        LambdaQueryWrapper<DagNodeInstanceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DagNodeInstanceDO::getDagInstanceId, instanceId);
        wrapper.orderByAsc(DagNodeInstanceDO::getCreatedAt);
        return mapper.selectList(wrapper);
    }

    // ==================== 内部方法 ====================

    /**
     * 收集 Spring 容器中所有 Agent，按 type() 字符串值索引。
     */
    private Map<String, Agent> collectAgents() {
        List<Agent> agentList = agentsProvider.getIfAvailable();
        Map<String, Agent> agents = new HashMap<>();
        if (agentList != null) {
            for (Agent agent : agentList) {
                if (agent.type() != null) {
                    agents.put(agent.type().name(), agent);
                }
            }
        }
        return agents;
    }

    /**
     * 持久化执行结果。
     */
    private void persistResult(DagDefinitionDO defDO, DagExecutionResult result,
                                Map<String, Object> globalInputs) {
        DagInstanceMapper instMapper = instMapperProvider.getIfAvailable();
        DagNodeInstanceMapper nodeMapper = nodeMapperProvider.getIfAvailable();
        if (instMapper == null) {
            log.warn("[DAG] DagInstanceMapper 不可用，跳过执行记录持久化");
            return;
        }

        // 1. 持久化 DAG 实例
        DagInstanceDO inst = new DagInstanceDO();
        inst.setTenantId(defDO.getTenantId());
        inst.setDagDefinitionId(defDO.getId());
        inst.setDagName(defDO.getName());
        inst.setBizType(defDO.getBizType());
        inst.setStatus(result.getStatus() != null ? result.getStatus().name() : "UNKNOWN");
        inst.setGlobalInputsJson(serialize(globalInputs));
        inst.setNodeOutputsJson(serialize(result.getNodeOutputs()));
        inst.setTotalCostMs(result.getTotalCostMs());
        inst.setSuccessCount(result.getSuccessCount());
        inst.setFailedCount(result.getFailedCount());
        inst.setSkippedCount(result.getSkippedCount());
        inst.setTotalNodes(result.getTotalNodes());
        inst.setNote(result.getNote());
        instMapper.insert(inst);

        // 2. 持久化节点实例
        if (nodeMapper == null) {
            log.warn("[DAG] DagNodeInstanceMapper 不可用，跳过节点明细持久化");
            return;
        }
        if (result.getNodeStatuses() != null) {
            for (Map.Entry<String, DagNodeStatus> entry : result.getNodeStatuses().entrySet()) {
                DagNodeInstanceDO nodeDO = new DagNodeInstanceDO();
                nodeDO.setTenantId(defDO.getTenantId());
                nodeDO.setDagInstanceId(inst.getId());
                nodeDO.setNodeName(entry.getKey());
                nodeDO.setStatus(entry.getValue().name());
                Object output = result.getNodeOutputs() != null
                        ? result.getNodeOutputs().get(entry.getKey()) : null;
                nodeDO.setOutputJson(serialize(output));
                String error = result.getNodeErrors() != null
                        ? result.getNodeErrors().get(entry.getKey()) : null;
                nodeDO.setErrorMessage(error);
                Integer retryCount = result.getNodeRetryCounts() != null
                        ? result.getNodeRetryCounts().get(entry.getKey()) : 0;
                nodeDO.setRetryCount(retryCount != null ? retryCount : 0);
                nodeMapper.insert(nodeDO);
            }
        }
    }

    /**
     * 序列化为 JSON。
     */
    private String serialize(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[DAG] JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 反序列化 JSON 为 DAG 定义。
     */
    private DagDefinition deserialize(String json) {
        try {
            return objectMapper.readValue(json, DagDefinition.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DAG 定义 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }
}
