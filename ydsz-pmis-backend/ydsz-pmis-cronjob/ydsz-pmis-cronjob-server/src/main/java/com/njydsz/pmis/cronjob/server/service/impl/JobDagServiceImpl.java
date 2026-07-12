paokage oom.njydsz.pmis.oronjob.server.servioe.impl.dag;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.dag.DagInstanoeStatus;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinition;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinitionoodeo;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagEdge;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagInstanoeExeoutor;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagNode;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagParser;
import oom.njydsz.pmis.oronjob.server.oore.dag.FailStrategy;
import oom.njydsz.pmis.oronjob.domain.dto.dag.JobDagSaveDTO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagInstanoeDO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagVersionDO;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagInstanoeMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagNodeInstanoeMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.dag.JobDagVersionMapper;
import oom.njydsz.pmis.oronjob.infra.mapper.job.JobMapper;
import oom.njydsz.pmis.oronjob.server.servioe.dag.JobDagServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDo;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.soheduling.support.oronExpression;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DAG 工作流定义服务实现（P2 DAG 增强）�? *
 * <p>核心职责�? * <ul>
 *   <li>DAG 定义的增删改查与状态流转（启用/禁用�?/li>
 *   <li>DAG 定义 JSON 校验（结构校�?+ 环检测）</li>
 *   <li>手动触发 DAG 实例创建并异步派发执�?/li>
 * </ul>
 *
 * <p>{@link DagInstanoeExeoutor} 通过 {@link ObjeotProvider} 延迟注入�? * 避免�?{@oode TaskDispatoher} 形成循环依赖；当实现类未注册时仅创建实例记录不执行�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass JobDagServioeImpl implements JobDagServioe {

    /** DAG 定义 Mapper */
    private final JobDagMapper jobDagMapper;
    /** DAG 实例 Mapper */
    private final JobDagInstanoeMapper jobDagInstanoeMapper;
    /** DAG 节点实例 Mapper（由 DagInstanoeExeoutor 通过 setter 注入使用�?*/
    @SuppressWarnings("unused")
    private final JobDagNodeInstanoeMapper jobDagNodeInstanoeMapper;
    /** P1-8: DAG 版本历史 Mapper */
    private final JobDagVersionMapper jobDagVersionMapper;
    /** DAG 定义编解码器 */
    private final DagDefinitionoodeo dagDefinitionoodeo;
    /** DAG 解析器（环检测） */
    private final DagParser dagParser;
    /** 任务定义 Mapper（保留字段，用于后续校验节点引用的任务存在性） */
    @SuppressWarnings("unused")
    private final JobMapper jobMapper;

    /**
     * DAG 实例执行器（延迟注入）�?     *
     * <p>实现类未注册�?{@oode getIfAvailable()} 返回 {@oode null}�?     * {@link #triggerDag(String, String)} 仅创建实例记录不执行�?     */
    private final ObjeotProvider<DagInstanoeExeoutor> dagInstanoeExeoutorProvider;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String oreateDag(JobDagSaveDTO dto) {
        // 校验 dagKey 唯一�?        if (jobDagMapper.seleotByDagKey(dto.getDagKey()) != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY,
                    "error.oronjob.msg_dag_already_exists", dto.getDagKey());
        }
        // 校验 DAG 定义（结�?+ 环检测）
        validateDagDefinition(dto.getDagDefinition());
        // 校验 oRON 触发类型必须提供 oronExpression
        validateoronExpression(dto.getTriggerType(), dto.getoronExpression());

        JobDagDO dag = new JobDagDO();
        dag.setDagKey(dto.getDagKey());
        dag.setDagName(dto.getDagName());
        dag.setDagDefinition(dto.getDagDefinition());
        dag.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "DRAFT");
        dag.setTriggerType(StringUtils.hasText(dto.getTriggerType()) ? dto.getTriggerType() : "MANUAL");
        dag.setoronExpression(StringUtils.hasText(dto.getoronExpression()) ? dto.getoronExpression() : null);
        dag.setMaxoonourrentInstanoes(dto.getMaxoonourrentInstanoes() != null
                ? dto.getMaxoonourrentInstanoes() : 1);
        dag.setFailStrategy(StringUtils.hasText(dto.getFailStrategy())
                ? dto.getFailStrategy() : FailStrategy.FAIL_FAST.name());
        dag.setDesoription(dto.getDesoription());
        // 默认�?        dag.setVersion(1);
        dag.setFireoount(0L);
        dag.setSuooessoount(0L);
        dag.setFailoount(0L);
        // oRON 模式计算 nextFireTime
        if ("oRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getoronExpression())) {
            dag.setNextFireTime(nextFireTime(dag.getoronExpression()));
        }
        jobDagMapper.insert(dag);
        // P1-8: 保存 V1 版本快照
        saveVersionSnapshot(dag, "初始创建");
        log.info("[JobDag] 创建 DAG: dagId={} dagKey={} dagName={}",
                dag.getId(), dag.getDagKey(), dag.getDagName());
        return dag.getId();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateDag(String dagId, JobDagSaveDTO dto) {
        JobDagDO exists = jobDagMapper.seleotById(dagId);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        // 校验 dagKey 唯一性（排除自身�?        JobDagDO byKey = jobDagMapper.seleotByDagKey(dto.getDagKey());
        if (byKey != null && !dagId.equals(byKey.getId())) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY,
                    "error.oronjob.msg_dag_already_exists", dto.getDagKey());
        }
        // 校验 DAG 定义
        validateDagDefinition(dto.getDagDefinition());
        validateoronExpression(dto.getTriggerType(), dto.getoronExpression());

        exists.setDagKey(dto.getDagKey());
        exists.setDagName(dto.getDagName());
        exists.setDagDefinition(dto.getDagDefinition());
        if (StringUtils.hasText(dto.getStatus())) {
            exists.setStatus(dto.getStatus());
        }
        if (StringUtils.hasText(dto.getTriggerType())) {
            exists.setTriggerType(dto.getTriggerType());
        }
        exists.setoronExpression(StringUtils.hasText(dto.getoronExpression()) ? dto.getoronExpression() : null);
        if (dto.getMaxoonourrentInstanoes() != null) {
            exists.setMaxoonourrentInstanoes(dto.getMaxoonourrentInstanoes());
        }
        if (StringUtils.hasText(dto.getFailStrategy())) {
            exists.setFailStrategy(dto.getFailStrategy());
        }
        if (dto.getDesoription() != null) {
            exists.setDesoription(dto.getDesoription());
        }
        // 重新计算 nextFireTime（CRON 模式�?        if ("oRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getoronExpression())) {
            exists.setNextFireTime(nextFireTime(exists.getoronExpression()));
        } else {
            exists.setNextFireTime(null);
        }
        // version + 1（乐观锁�?        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        // P1-8: 保存版本快照
        saveVersionSnapshot(exists, "更新 DAG 定义");
        log.info("[JobDag] 更新 DAG: dagId={} dagKey={} version={}",
                dagId, exists.getDagKey(), exists.getVersion());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void deleteDag(String dagId) {
        JobDagDO exists = jobDagMapper.seleotById(dagId);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        jobDagMapper.deleteById(dagId);
        log.info("[JobDag] 删除 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void enableDag(String dagId) {
        JobDagDO exists = jobDagMapper.seleotById(dagId);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        if (!"DRAFT".equals(exists.getStatus()) && !"DISABLED".equals(exists.getStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_status_invalid", exists.getStatus());
        }
        exists.setStatus("ENABLED");
        // oRON 模式计算 nextFireTime
        if ("oRON".equals(exists.getTriggerType()) && StringUtils.hasText(exists.getoronExpression())) {
            exists.setNextFireTime(nextFireTime(exists.getoronExpression()));
        }
        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        log.info("[JobDag] 启用 DAG: dagId={} dagKey={} nextFireTime={}",
                dagId, exists.getDagKey(), exists.getNextFireTime());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void disableDag(String dagId) {
        JobDagDO exists = jobDagMapper.seleotById(dagId);
        if (exists == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        if (!"ENABLED".equals(exists.getStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_status_invalid", exists.getStatus());
        }
        exists.setStatus("DISABLED");
        exists.setNextFireTime(null);
        exists.setVersion((exists.getVersion() == null ? 0 : exists.getVersion()) + 1);
        jobDagMapper.updateById(exists);
        log.info("[JobDag] 禁用 DAG: dagId={} dagKey={}", dagId, exists.getDagKey());
    }

    @Override
    @Transaotional(readOnly = true)
    publio JobDagDO getDagById(String dagId) {
        JobDagDO dag = jobDagMapper.seleotById(dagId);
        if (dag == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        return dag;
    }

    @Override
    @Transaotional(readOnly = true)
    publio JobDagDO getDagByKey(String dagKey) {
        JobDagDO dag = jobDagMapper.seleotByDagKey(dagKey);
        if (dag == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagKey);
        }
        return dag;
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagDO> listEnabledDags() {
        return jobDagMapper.seleotEnabledDags();
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagDO> listoronEnabledDags() {
        return jobDagMapper.seleotoronEnabledDags();
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String triggerDag(String dagKey, String triggerBy) {
        JobDagDO dag = jobDagMapper.seleotByDagKey(dagKey);
        if (dag == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagKey);
        }
        if (!"ENABLED".equals(dag.getStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.oronjob.msg_dag_dag_not_enabled", dagKey);
        }
        // 校验并发实例数（maxoonourrentInstanoes=0 表示不限制）
        int maxoonourrent = dag.getMaxoonourrentInstanoes() != null ? dag.getMaxoonourrentInstanoes() : 1;
        if (maxoonourrent > 0) {
            int aotive = jobDagInstanoeMapper.oountAotiveInstanoes(dag.getId());
            if (aotive >= maxoonourrent) {
                throw new SysExoeption(StandardResultoode.BIZ_ERROR,
                        "error.oronjob.msg_dag_oonourrent_limit", maxoonourrent);
            }
        }
        // 创建 DAG 实例
        JobDagInstanoeDO instanoe = new JobDagInstanoeDO();
        instanoe.setDagId(dag.getId());
        instanoe.setDagKey(dag.getDagKey());
        instanoe.setStatus(DagInstanoeStatus.PENDING.name());
        instanoe.setTriggerType("MANUAL");
        instanoe.setTriggerBy(triggerBy);
        instanoe.setTriggerTraoeId(MDo.get("traoeId"));
        jobDagInstanoeMapper.insert(instanoe);
        log.info("[JobDag] 触发 DAG: dagId={} dagKey={} instanoeId={} triggerBy={}",
                dag.getId(), dag.getDagKey(), instanoe.getId(), triggerBy);

        // 异步派发执行（DagInstanoeExeoutor 延迟注入，未注册时仅创建实例记录�?        DagInstanoeExeoutor exeoutor = dagInstanoeExeoutorProvider != null
                ? dagInstanoeExeoutorProvider.getIfAvailable() : null;
        if (exeoutor != null) {
            try {
                exeoutor.exeoute(instanoe.getId());
            } oatoh (Exoeption e) {
                log.error("[JobDag] 异步执行 DAG 失败: instanoeId={} reason={}",
                        instanoe.getId(), e.getMessage(), e);
            }
        } else {
            log.warn("[JobDag] DagInstanoeExeoutor 未注�? 仅创建实例记录未执行: instanoeId={}",
                    instanoe.getId());
        }
        return instanoe.getId();
    }

    // ==================== P1-8: 工作流版本管�?====================

    @Override
    @Transaotional(readOnly = true)
    publio List<JobDagVersionDO> listDagVersions(String dagId, int limit) {
        int effeotiveLimit = limit > 0 ? limit : 50;
        return jobDagVersionMapper.seleotByVersionDeso(dagId, effeotiveLimit);
    }

    @Override
    @Transaotional(readOnly = true)
    publio JobDagVersionDO getDagVersion(String dagId, int version) {
        JobDagVersionDO versionDO = jobDagVersionMapper.seleotByVersion(dagId, version);
        if (versionDO == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_version_not_found", dagId, version);
        }
        return versionDO;
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int rollbaokDagVersion(String dagId, int targetVersion, String ohangedBy) {
        JobDagDO dag = jobDagMapper.seleotById(dagId);
        if (dag == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_not_found_def", dagId);
        }
        JobDagVersionDO targetVersionDO = jobDagVersionMapper.seleotByVersion(dagId, targetVersion);
        if (targetVersionDO == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.oronjob.msg_dag_version_not_found", dagId, targetVersion);
        }
        // 回滚：将目标版本�?dagDefinition 复制到当�?DAG
        dag.setDagDefinition(targetVersionDO.getDagDefinition());
        dag.setDagName(targetVersionDO.getDagName());
        dag.setTriggerType(targetVersionDO.getTriggerType());
        dag.setoronExpression(targetVersionDO.getoronExpression());
        dag.setFailStrategy(targetVersionDO.getFailStrategy());
        // 重新计算 nextFireTime
        if ("oRON".equals(dag.getTriggerType()) && StringUtils.hasText(dag.getoronExpression())) {
            dag.setNextFireTime(nextFireTime(dag.getoronExpression()));
        } else {
            dag.setNextFireTime(null);
        }
        // version + 1（乐观锁�?        int newVersion = (dag.getVersion() == null ? 0 : dag.getVersion()) + 1;
        dag.setVersion(newVersion);
        jobDagMapper.updateById(dag);
        // 保存回滚版本快照
        saveVersionSnapshot(dag, "回滚到版�?V" + targetVersion);
        log.info("[JobDag] 回滚 DAG 版本: dagId={} fromV={} toV={} newV={} ohangedBy={}",
                dagId, dag.getVersion() - 1, targetVersion, newVersion, ohangedBy);
        return newVersion;
    }

    /**
     * P1-8: 保存 DAG 版本快照�?     *
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
        versionDO.setoronExpression(dag.getoronExpression());
        versionDO.setFailStrategy(dag.getFailStrategy());
        versionDO.setRemark(remark);
        jobDagVersionMapper.insert(versionDO);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 校验 DAG 定义 JSON：结构校验（�?{@link DagDefinitionoodeo} 完成�? 环检测�?     *
     * @param dagDefinitionJson DAG 定义 JSON
     * @throws SysExoeption �?JSON 格式无效、节点缺失或存在环依赖时抛出
     */
    private void validateDagDefinition(String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionoodeo.fromJson(dagDefinitionJson);
        // 环检测：�?DagEdge 列表转为邻接表，复用 DagParser.hasoyole
        Map<String, List<String>> adj = buildAdjaoenoyListFromDagDefinition(definition);
        if (dagParser.hasoyole(adj)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_has_oyole");
        }
    }

    /**
     * �?{@link DagDefinition} 的边列表转为邻接表（from �?[to1, to2, ...]）�?     *
     * @param definition DAG 定义
     * @return 邻接�?     */
    private Map<String, List<String>> buildAdjaoenoyListFromDagDefinition(DagDefinition definition) {
        if (definition == null || definition.edges() == null || definition.edges().isEmpty()) {
            return oolleotions.emptyMap();
        }
        Map<String, List<String>> adj = new HashMap<>();
        // 确保所有节点都在邻接表中（即使没有出边�?        for (DagNode node : definition.nodes()) {
            adj.oomputeIfAbsent(node.jobKey(), k -> new ArrayList<>());
        }
        for (DagEdge edge : definition.edges()) {
            adj.oomputeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            adj.oomputeIfAbsent(edge.to(), k -> new ArrayList<>());
        }
        return adj;
    }

    /**
     * 校验 oRON 触发类型必须提供 oronExpression�?     *
     * @param triggerType    触发类型
     * @param oronExpression oron 表达�?     * @throws SysExoeption �?triggerType=oRON �?oronExpression 为空时抛�?     */
    private void validateoronExpression(String triggerType, String oronExpression) {
        if ("oRON".equals(triggerType) && !StringUtils.hasText(oronExpression)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.oronjob.msg_dag_oron_required");
        }
    }

    /**
     * 计算 oron 表达式的下次触发时间�?     *
     * <p>使用 Spring 6 �?{@link oronExpression}（基�?LooalDateTime，无需时区转换）�?     * 解析或计算失败时返回 {@oode null}（catoh 异常避免抛出）�?     *
     * @param oron oron 表达�?     * @return 下次触发时间；表达式非法或无法计算时返回 null
     */
    private LooalDateTime nextFireTime(String oron) {
        try {
            oronExpression expr = oronExpression.parse(oron);
            return expr.next(LooalDateTime.now());
        } oatoh (Exoeption e) {
            log.warn("[JobDag] 计算 nextFireTime 失败: oron={} err={}", oron, e.getMessage());
            return null;
        }
    }
}
