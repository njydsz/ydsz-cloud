package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionWbsTaskDO;
import com.njydsz.project.domain.repository.execution.IExecutionWbsTaskRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionWbsTaskMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionWbsTask Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionWbsTaskRepository extends ServiceImpl<ExecutionWbsTaskMapper, ExecutionWbsTaskDO>
        implements IExecutionWbsTaskRepository {
}
