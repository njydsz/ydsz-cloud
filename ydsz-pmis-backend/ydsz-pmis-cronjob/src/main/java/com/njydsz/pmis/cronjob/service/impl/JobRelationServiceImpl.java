package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.dag.DagParser;
import com.njydsz.pmis.cronjob.core.dag.FailStrategy;
import com.njydsz.pmis.cronjob.entity.JobRelationDO;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.mapper.JobRelationMapper;
import com.njydsz.pmis.cronjob.service.JobRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRelationServiceImpl implements JobRelationService {

    private final JobRelationMapper jobRelationMapper;
    private final JobMapper jobMapper;
    private final DagParser dagParser;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addRelation(String parentJobId, String childJobId, String failStrategy) {
        // 校验任务存在性
        validateJobExists(parentJobId, "前置任务");
        validateJobExists(childJobId, "后继任务");
        // 校验自依赖
        if (parentJobId.equals(childJobId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_dag_self_ref");
        }
        // 环检测：添加 parent→child 后是否形成环
        List<JobRelationDO> existing = jobRelationMapper.selectAllRelations();
        if (dagParser.wouldCreateCycle(parentJobId, childJobId, existing)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.cronjob.msg_dag_cycle",
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
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_dag_not_found");
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
            throw new BizException(BizErrorCode.NOT_FOUND, "error.cronjob.msg_dag_job_not_found", label, jobId);
        }
    }
}
