package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.domain.repository.execution.IExecutionRiskRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionRiskMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionRisk Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionRiskRepository extends ServiceImpl<ExecutionRiskMapper, ExecutionRisk>
        implements IExecutionRiskRepository {
}
