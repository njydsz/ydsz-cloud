package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionClosureDO;
import com.njydsz.project.domain.repository.execution.IExecutionClosureRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionClosureMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionClosure Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionClosureRepository extends ServiceImpl<ExecutionClosureMapper, ExecutionClosureDO>
        implements IExecutionClosureRepository {
}
