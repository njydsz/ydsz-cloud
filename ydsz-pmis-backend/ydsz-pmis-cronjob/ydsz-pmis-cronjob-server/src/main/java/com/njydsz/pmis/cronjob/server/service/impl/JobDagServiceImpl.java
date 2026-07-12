package com.njydsz.pmis.cronjob.server.service.impl.dag;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.dag.DagInstanceStatus;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.server.core.dag.DagEdge;
import com.njydsz.pmis.cronjob.server.core.dag.DagInstanceExecutor;
import com.njydsz.pmis.cronjob.server.core.dag.DagNode;
import com.njydsz.pmis.cronjob.server.core.dag.DagParser;
import com.njydsz.pmis.cronjob.server.core.dag.FailStrategy;
import com.njydsz.pmis.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagVersionDO;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagVersionMapper;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.server.service.dag.JobDagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 工作流定义服务实现（P2 DAG 增强）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>DAG 定义的增删改查与状态流转（启用/禁用）</li>
 *   <li>DAG 定义 JSON 校验（结构校验 + 环检测）</li>
 *   <li>手动触发 DAG 实例创建并异步派发执行</li>
 * </ul>
 *
 * <p>{@link DagInstanceExecutor} 通过 {@link ObjectProvider} 延迟注入，
 * 避免与 {@code TaskDispatcher} 形成循环依赖；当实现类未注册时仅创建实例记录不执行。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDagServiceImpl implements JobDagService {

    /** DAG 定义 Mapper */
    private final JobDagMapper jobDagMapper;
    /** DAG 实例 Mapper */
    private final JobDagInstanceMapper jobDagInstanceMapper;
    /** DAG 节点实例 Mapper（由 DagInstanceExecutor 通过 setter 注入使用） */
    @SuppressWarnings("unused")
    private final JobDagNodeInstanceMapper jobDagNodeInstanceMapper;
    /** P1-8: DAG 版本历史 Mapper */
    private final JobDagVersionMapper jobDagVersionMapper;
    /** DAG 定义编解码器 */
    private final DagDefinitionCodec dagDefinitionCodec;
    /** DAG 解析器（环检测） */
    private final DagParser dagParser;
    /** 任务定义 Mapper（保留字段，用于后续校验节点引用的任务存在性） */
    @SuppressWarnings("unused")
    private final JobMapper jobMapper;

    /**
     * DAG 实例执行器（延迟注入）。
     *
     * <p>实现类未注册时 {@code getIfAvailable()} 返回 {@code null}，
     * {@link #triggerDag(String, String)} 仅创建实例记录不执行。
     */
    private final ObjectProvider<DagInstanceExecutor> dagInstanceExecutorProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createDag(JobDagSaveDTO dto) {
        // 校验 dagKey 唯一性
        if (jobDagMapper.selectByDagKey(dto.getDagKey()) != null) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY,
                    "error.cronjob.msg_dag_already_exists", dto.getDagKey());
        }
        // 校验 DAG 定义（结构 + 环检测）
        validateDagDefinition(dto.getDagDefinition());
        // 校验 CRON 触发类型必须提供 cronExpression
        validateCronExpression(dto.getTriggerType(), dto.getCronExpression());

        JobDagDO dag = new JobDagDO();
        dag.setDagKey(dto.getDagKey());
        dag.setDagName(dto.getDagName());
        dag.setDagDefinition(dto.getDagDefinition());
        dag.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "DRAFT");
        dag.setTriggerType(StringUtils.hasText(dto.getTriggerType()) ? dto.getTriggerType() : "MANUAL");
        dag.setCronExpression(StringUtils.hasText(dto.getCronExpression()) ? dto.getCronExpression() : null);
        dag.setMaxConcurrentInstances(dto.getMaxConcurrentInstances() != null
                ? dto.getMaxConcurrentInstances() : 1);
        dag.setFailStrategy(StringUtils.hasText(dto.getFailStrategy())
                ? dto.getFailStrategy() : FailStrategy.FAIL_FAST.name());
        dag.setDescription(dto.getDescription());
        // 默认值
        dag.setVersion(1);
        dag.setFireCount(0L);
        dag.setSuccessCount(0L);
        dag.setFailCount(0L);
        // CRON 模式计算 nextFireTime
        if ("CRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getCronExpression())) {
            dag.setNextFireTime(nextFireTime(dag.getCronExpression()));
        }
        jobDagMapper.insert(dag);
        // P1-8: 保存 V1 版本快照
        saveVersionSnapshot(dag, "初始创建");
        log.info("[JobDag] 创建 DAG: dagId={} dagKey={} dagName={}",
                dag.getId(), dag.getDagKey(), dag.getDagName());
        return dag.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDag(String dagId, JobDagSaveDTO dto) {
        JobDagDO exists = jobDagMapper.selectById(dagId);
        if (exists == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        // 校验 dagKey 唯一性（排除自身）
        JobDagDO byKey = jobDagMapper.selectByDagKey(dto.getDagKey());
        if (byKey != null && !dagId.equals(byKey.getId())) {
            throw new SysException(StandardResultCode.DUPLICATE_KEY,
                    "error.cronjob.msg_dag_already_exists", dto.getDagKey());
        }
        // 校验 DAG 定义
        validateDagDefinition(dto.getDagDefinition());
        validateCronExpression(dto.getTriggerType(), dto.getCronExpression());

        exists.setDagKey(dto.getDagKey());
        exists.setDagName(dto.getDagName());
        exists.setDagDefinition(dto.getDagDefinition());
        if (StringUtils.hasText(dto.getStatus())) {
            exists.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getTriggerType())) {
            exists.setTriggerType(dto.getTriggerType());
        }
        exists.setCronExpression(StringUtils.hasText(dto.getCronExpression()) ? dto.getCronExpression() : null);
        if (dto.getMaxConcurrentInstances() != null) {
            exists.setMaxConcurrentInstances(dto.getMaxConcurrentInstances());
        }
        if (StringUtils.hasText(dto.getFailStrategy())) {
            exists.setFailStrategy(dto.getFailStrategy());
        }
        if (dto.getDescription() != null) {
            exists.setDescription(dto.getDescription());
        }
        // 重新计算 nextFireTime（CRON 模式）
        if ("CRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getCronExpression())) {
            exists.setNextFireTime(nextFireTime(exists.getCronExpression()));
        } else {
            exists.setNextFireTime(null);
        }
        // version + 1（乐观锁）
        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        // P1-8: 保存版本快照
        saveVersionSnapshot(exists, "更新 DAG 定义");
        log.info("[JobDag] 更新 DAG: dagId={} dagKey={} version={}",
                dagId, exists.getDagKey(), exists.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDag(String dagId) {
        JobDagDO exists = jobDagMapper.selectById(dagId);
        if (exists == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        jobDagMapper.deleteById(dagId);
        log.info("[JobDag] 删除 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enableDag(String dagId) {
        JobDagDO exists = jobDagMapper.selectById(dagId);
        if (exists == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        if (!"DRAFT".equals(exists.getStatus()) && !"DISABLED".equals(exists.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_status_invalid", exists.getStatus());
        }
        exists.setStatus("ENABLED");
        // CRON 模式计算 nextFireTime
        if ("CRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getCronExpression())) {
            exists.setNextFireTime(nextFireTime(exists.getCronExpression()));
        }
        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        log.info("[JobDag] 启用 DAG: dagId={} dagKey={} nextFireTime={}",
                dagId, exists.getDagKey(), exists.getNextFireTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableDag(String dagId) {
        JobDagDO exists = jobDagMapper.selectById(dagId);
        if (exists == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        if (!"ENABLED".equals(exists.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_status_invalid", exists.getStatus());
        }
        exists.setStatus("DISABLED");
        exists.setNextFireTime(null);
        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        log.info("[JobDag] 禁用 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
    }

    @Override
    @Transactional(readOnly = true)
    public JobDagDO getDagById(String dagId) {
        JobDagDO dag = jobDagMapper.selectById(dagId);
        if (dag == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        return dag;
    }

    @Override
    @Transactional(readOnly = true)
    public JobDagDO getDagByKey(String dagKey) {
        JobDagDO dag = jobDagMapper.selectByDagKey(dagKey);
        if (dag == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagKey);
        }
        return dag;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagDO> listEnabledDags() {
        return jobDagMapper.selectEnabledDags();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagDO> listCronEnabledDags() {
        return jobDagMapper.selectCronEnabledDags();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String triggerDag(String dagKey, String triggerBy) {
        JobDagDO dag = jobDagMapper.selectByDagKey(dagKey);
        if (dag == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagKey);
        }
        if (!"ENABLED".equals(dag.getStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_dag_not_enabled", dagKey);
        }
        // 校验并发实例数（maxConcurrentInstances=0 表示不限制）
        int maxConcurrent = dag.getMaxConcurrentInstances() != null ? dag.getMaxConcurrentInstances() : 1;
        if (maxConcurrent > 0) {
            int active = jobDagInstanceMapper.countActiveInstances(dag.getId());
            if (active >= maxConcurrent) {
                throw new SysException(StandardResultCode.BIZ_ERROR,
                        "error.cronjob.msg_dag_concurrent_limit", maxConcurrent);
            }
        }
        // 创建 DAG 实例
        JobDagInstanceDO instance = new JobDagInstanceDO();
        instance.setDagId(dag.getId());
        instance.setDagKey(dag.getDagKey());
        instance.setStatus(DagInstanceStatus.PENDING.name());
        instance.setTriggerType("MANUAL");
        instance.setTriggerBy(triggerBy);
        instance.setTriggerTraceId(MDC.get("traceId"));
        jobDagInstanceMapper.insert(instance);
        log.info("[JobDag] 触发 DAG: dagId={} dagKey={} instanceId={} triggerBy={}",
                dag.getId(), dag.getDagKey(), instance.getId(), triggerBy);

        // 异步派发执行（DagInstanceExecutor 延迟注入，未注册时仅创建实例记录）
        DagInstanceExecutor executor = dagInstanceExecutorProvider != null
                ? dagInstanceExecutorProvider.getIfAvailable() : null;
        if (executor != null) {
            try {
                executor.execute(instance.getId());
            } catch (Exception e) {
                log.error("[JobDag] 异步执行 DAG 失败: instanceId={} reason={}",
                        instance.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("[JobDag] DagInstanceExecutor 未注册, 仅创建实例记录未执行: instanceId={}",
                    instance.getId());
        }
        return instance.getId();
    }

    // ==================== P1-8: 工作流版本管理 ====================

    @Override
    @Transactional(readOnly = true)
    public List<JobDagVersionDO> listDagVersions(String dagId, int limit) {
        int effectiveLimit = limit > 0 ? limit : 50;
        return jobDagVersionMapper.selectByVersionDesc(dagId, effectiveLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public JobDagVersionDO getDagVersion(String dagId, int version) {
        JobDagVersionDO versionDO = jobDagVersionMapper.selectByVersion(dagId, version);
        if (versionDO == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_version_not_found", dagId, version);
        }
        return versionDO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackDagVersion(String dagId, int targetVersion, String changedBy) {
        JobDagDO dag = jobDagMapper.selectById(dagId);
        if (dag == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", dagId);
        }
        JobDagVersionDO targetVersionDO = jobDagVersionMapper.selectByVersion(dagId, targetVersion);
        if (targetVersionDO == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_version_not_found", dagId, targetVersion);
        }
        // 回滚：将目标版本的 dagDefinition 复制到当前 DAG
        dag.setDagDefinition(targetVersionDO.getDagDefinition());
        dag.setDagName(targetVersionDO.getDagName());
        dag.setTriggerType(targetVersionDO.getTriggerType());
        dag.setCronExpression(targetVersionDO.getCronExpression());
        dag.setFailStrategy(targetVersionDO.getFailStrategy());
        // 重新计算 nextFireTime
        if ("CRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getCronExpression())) {
            dag.setNextFireTime(nextFireTime(dag.getCronExpression()));
        } else {
            dag.setNextFireTime(null);
        }
        // version + 1（乐观锁）
        int newVersion = (dag.getVersion() == null ? 0 : dag.getVersion()) + 1;
        dag.setVersion(newVersion);
        jobDagMapper.updateById(dag);
        // 保存回滚版本快照
        saveVersionSnapshot(dag, "回滚到版本 V" + targetVersion);
        log.info("[JobDag] 回滚 DAG 版本: dagId={} fromV={} toV={} newV={} changedBy={}",
                dagId, dag.getVersion() - 1, targetVersion, newVersion, changedBy);
        return newVersion;
    }

    /**
     * P1-8: 保存 DAG 版本快照。
     *
     * @param dag    DAG 定义
     * @param remark 版本备注
     */
    private void saveVersionSnapshot(JobDagDO dag, String remark) {
        JobDagVersionDO versionDO = new JobDagVersionDO();
        versionDO.setDagId(dag.getId());
        versionDO.setDagKey(dag.getDagKey());
        versionDO.setVersion(dag.getVersion());
        versionDO.setDagDefinition(dag.getDagDefinition());
        versionDO.setDagName(dag.getDagName());
        versionDO.setTriggerType(dag.getTriggerType());
        versionDO.setCronExpression(dag.getCronExpression());
        versionDO.setFailStrategy(dag.getFailStrategy());
        versionDO.setRemark(remark);
        jobDagVersionMapper.insert(versionDO);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验 DAG 定义 JSON：结构校验（由 {@link DagDefinitionCodec} 完成）+ 环检测。
     *
     * @param dagDefinitionJson DAG 定义 JSON
     * @throws SysException 当 JSON 格式无效、节点缺失或存在环依赖时抛出
     */
    private void validateDagDefinition(String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
        // 环检测：将 DagEdge 列表转为邻接表，复用 DagParser.hasCycle
        Map<String, List<String>> adj = buildAdjacencyListFromDagDefinition(definition);
        if (dagParser.hasCycle(adj)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_has_cycle");
        }
    }

    /**
     * 将 {@link DagDefinition} 的边列表转为邻接表（from → [to1, to2, ...]）。
     *
     * @param definition DAG 定义
     * @return 邻接表
     */
    private Map<String, List<String>> buildAdjacencyListFromDagDefinition(DagDefinition definition) {
        if (definition == null || definition.edges() == null || definition.edges().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> adj = new HashMap<>();
        // 确保所有节点都在邻接表中（即使没有出边）
        for (DagNode node : definition.nodes()) {
            adj.computeIfAbsent(node.jobKey(), k -> new ArrayList<>());
        }
        for (DagEdge edge : definition.edges()) {
            adj.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            adj.computeIfAbsent(edge.to(), k -> new ArrayList<>());
        }
        return adj;
    }

    /**
     * 校验 CRON 触发类型必须提供 cronExpression。
     *
     * @param triggerType    触发类型
     * @param cronExpression Cron 表达式
     * @throws SysException 当 triggerType=CRON 且 cronExpression 为空时抛出
     */
    private void validateCronExpression(String triggerType, String cronExpression) {
        if ("CRON".equals(triggerType) && !StringUtils.hasText(cronExpression)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_cron_required");
        }
    }

    /**
     * 计算 Cron 表达式的下次触发时间。
     *
     * <p>使用 Spring 6 的 {@link CronExpression}（基于 LocalDateTime，无需时区转换）。
     * 解析或计算失败时返回 {@code null}（catch 异常避免抛出）。
     *
     * @param cron Cron 表达式
     * @return 下次触发时间；表达式非法或无法计算时返回 null
     */
    private LocalDateTime nextFireTime(String cron) {
        try {
            CronExpression expr = CronExpression.parse(cron);
            return expr.next(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("[JobDag] 计算 nextFireTime 失败: cron={} err={}", cron, e.getMessage());
            return null;
        }
    }
}
