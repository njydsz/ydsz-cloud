package com.njydsz.pmis.cronjob.server.service.impl.job;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.cronjob.domain.entity.job.JobRelationDO;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobRelationMapper;
import com.njydsz.pmis.cronjob.server.core.dag.DagParser;
import com.njydsz.pmis.cronjob.server.core.dag.FailStrategy;
import com.njydsz.pmis.cronjob.server.service.job.JobRelationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务依赖关系服务实现（P4 DAG 工作流）。
 *
 * <p>核心职责：
 * <ul>
 *   <li>添加依赖关系时执行环检测（防止形成循环依赖）</li>
 *   <li>校验任务存在性</li>
 *   <li>校验自依赖</li>
 * </ul>
 *
 * @deprecated P3-2-merge: 推荐使用 DAG 定义服务 ({@code JobDagService}) 管理任务依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRelationServiceImpl implements JobRelationService {

    /** 任务依赖关系 Mapper */
    private final JobRelationMapper jobRelationMapper;
    /** 任务定义 Mapper（校验任务存在性） */
    private final JobMapper jobMapper;
    /** DAG 解析器（环检测） */
    private final DagParser dagParser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addRelation(String parentJobId, String childJobId, String failStrategy) {
        // 校验任务存在性
        validateJobExists(parentJobId, "前置任务");
        validateJobExists(childJobId, "后继任务");
        // 校验自依赖
        if (parentJobId.equals(childJobId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_self_ref");
        }
        // 环检测：添加 parent→child 后是否形成环
        List<JobRelationDO> existing = jobRelationMapper.selectAllRelations();
        if (dagParser.wouldCreateCycle(parentJobId, childJobId, existing)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.cronjob.msg_dag_cycle",
                    parentJobId, childJobId);
        }
        JobRelationDO relation = new JobRelationDO();
        relation.setParentJobId(parentJobId);
        relation.setChildJobId(childJobId);
        relation.setFailStrategy(StringUtils.hasText(failStrategy) ? failStrategy : FailStrategy.FAIL_FAST.name());
        jobRelationMapper.insert(relation);
        log.info("[DagRelation] 添加依赖: parent={} child={} strategy={}",
                parentJobId, childJobId, relation.getFailStrategy());
        return relation.getId();
    }

    @Override
    public void removeRelation(String relationId) {
        JobRelationDO relation = jobRelationMapper.selectById(relationId);
        if (relation == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.cronjob.msg_dag_not_found");
        }
        jobRelationMapper.deleteById(relationId);
        log.info("[DagRelation] 删除依赖: id={} parent={} child={}",
                relationId, relation.getParentJobId(), relation.getChildJobId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobRelationDO> getChildren(String parentJobId) {
        return jobRelationMapper.selectByParentJobId(parentJobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobRelationDO> getParents(String childJobId) {
        return jobRelationMapper.selectByChildJobId(childJobId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobRelationDO> getAllRelations() {
        return jobRelationMapper.selectAllRelations();
    }

    private void validateJobExists(String jobId, String label) {
        if (jobMapper.selectById(jobId) == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.cronjob.msg_dag_job_not_found", label, jobId);
        }
    }
}
