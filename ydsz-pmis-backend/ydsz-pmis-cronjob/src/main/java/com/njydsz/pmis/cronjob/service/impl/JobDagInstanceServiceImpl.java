package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.dag.DagInstanceStatus;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.service.JobDagInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * DAG 工作流实例服务实现（P2 DAG 增强）。
 *
 * <p>负责 DAG 实例的查询、状态流转（暂停/恢复/取消）及上下文管理。
 * 状态流转使用 CAS 更新（{@code casUpdateStatus}）避免并发覆盖。
 *
 * @author ydsz-pmis-team
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

    @Override
    @Transactional(readOnly = true)
    public JobDagInstanceDO getInstanceById(String instanceId) {
        JobDagInstanceDO instance = jobDagInstanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.cronjob.msg_dag_instance_not_found", instanceId);
        }
        return instance;
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagInstanceDO> listByDagId(String dagId, int limit) {
        int safeLimit = limit > 0 ? limit : 20;
        return jobDagInstanceMapper.selectByDagId(dagId, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagInstanceDO> listByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        return jobDagInstanceMapper.selectByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobDagNodeInstanceDO> listNodes(String dagInstanceId) {
        return jobDagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseInstance(String instanceId) {
        getInstanceById(instanceId);
        int rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                DagInstanceStatus.RUNNING.name(), DagInstanceStatus.PAUSED.name());
        if (rows == 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
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
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "error.cronjob.msg_dag_instance_not_running", instanceId);
        }
        log.info("[JobDagInstance] 恢复 DAG 实例: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelInstance(String instanceId) {
        getInstanceById(instanceId);
        // RUNNING → CANCELED
        int rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                DagInstanceStatus.RUNNING.name(), DagInstanceStatus.CANCELED.name());
        if (rows == 0) {
            // PAUSED → CANCELED
            rows = jobDagInstanceMapper.casUpdateStatus(instanceId,
                    DagInstanceStatus.PAUSED.name(), DagInstanceStatus.CANCELED.name());
        }
        if (rows == 0) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
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
}
