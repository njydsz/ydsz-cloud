package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandardDO;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryStandardRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionDeliveryStandardMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionDeliveryStandard Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionDeliveryStandardRepository extends ServiceImpl<ExecutionDeliveryStandardMapper, ExecutionDeliveryStandardDO>
        implements IExecutionDeliveryStandardRepository {
}
