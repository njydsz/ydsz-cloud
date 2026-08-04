package com.njydsz.cronjob.server.service.impl.dag;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.cronjob.domain.dag.DagInstanceStatus;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagInstance;
import com.njydsz.cronjob.domain.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.service.dag.JobDagInstanceService;
import com.njydsz.cronjob.server.vo.DagInstanceVisualizationVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * DAG 任务实例服务实现。
 *
 * <p>管理 DAG 任务实例 ({@code ydsz_job_dag_instance} / {@code ydsz_job_dag_node_instance})：
 *
 * <p>DAG 版本快照加载、节点入度计算、并行执行调度、失败重试、状态回滚。
 *
 * <p>支持分布式锁 + 抢占式调度（Leader / Leaderless 双模式）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDagInstanceServiceImpl implements JobDagInstanceService {

    /** DAG 实例 Mapper */
    private final JobDagInstanceMapper jobDagInstanceMapper;
    /** DAG 节点实例 Mapper */
    private final JobDagNodeInstanceMapper jobDagNodeInstanceMapper;
    /** DAG 定义 Mapper（用于查询 DAG 定义 JSON） */
    private final JobDagMapper jobDagMapper;
    /** DAG 定义 JSON 编解码器 */
    private final DagDefinitionCodec dagDefinitionCodec;

    @Override
    @Transactional(readOnly = true)
    public JobDagInstance getInstanceById(String instanceId) {
        JobDagInstance instance = jobDagInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_instance_not_found", instanceId);
        }
        return instance;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagInstance> listByDagId(String dagId, int limit) {
        int safeLimit = limit > 0 ? limit : 20;
        return jobDagInstanceMapper.selectByDagId(dagId, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagInstance> listByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        return jobDagInstanceMapper.selectByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagNodeInstance> listNodes(String dagInstanceId) {
        return jobDagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseInstance(String instanceId) {
        getInstanceById(instanceId);
        int rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                DagInstanceStatus.RUNNING.name(), DagInstanceStatus.PAUSED.name());
        if (rows == 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_instance_not_running", instanceId);
        }
        log.info("[JobDagInstance] 暂停 DAG 实例: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeInstance(String instanceId) {
        getInstanceById(instanceId);
        int rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                DagInstanceStatus.PAUSED.name(), DagInstanceStatus.RUNNING.name());
        if (rows == 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_instance_not_running", instanceId);
        }
        log.info("[JobDagInstance] 恢复 DAG 实例: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInstance(String instanceId) {
        getInstanceById(instanceId);
        // RUNNING → CANCELLED
        int rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                DagInstanceStatus.RUNNING.name(), DagInstanceStatus.CANCELLED.name());
        if (rows == 0) {
            // PAUSED → CANCELLED
            rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                    DagInstanceStatus.PAUSED.name(), DagInstanceStatus.CANCELLED.name());
        }
        if (rows == 0) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_instance_not_running", instanceId);
        }
        log.info("[JobDagInstance] 取消 DAG 实例: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateContext(String instanceId, String contextJson) {
        getInstanceById(instanceId);
        jobDagInstanceMapper.updateContext(instanceId, contextJson);
        log.info("[JobDagInstance] 更新 DAG 实例上下文: instanceId={}", instanceId);
    }

    @Override
    @Transactional(readOnly = true)
    public DagInstanceVisualizationVO getVisualization(String instanceId) {
        // 1. 查询 DAG 实例（不存在时抛 SysException）
        JobDagInstance instance = getInstanceById(instanceId);

        // 2. 查询 DAG 定义（通过实例.dagId 关联）
        JobDag dag = jobDagMapper.selectById(instance.getDagId());
        if (dag == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.cronjob.msg_dag_not_found_def", instance.getDagId());
        }

        // 3. 解析 DAG 定义 JSON（非法时抛 SysException）
        DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());

        // 4. 查询节点实例执行状态
        List<JobDagNodeInstance> nodeInstances = listNodes(instanceId);

        // 5. 组装可视化数据 VO
        DagInstanceVisualizationVO vo = new DagInstanceVisualizationVO();
        vo.setInstance(instance);
        vo.setDefinition(definition);
        vo.setNodeInstances(nodeInstances);
        return vo;
    }
}
