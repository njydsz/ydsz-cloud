package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryItemRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionDeliveryItemMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionDeliveryItem Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionDeliveryItemRepository extends ServiceImpl<ExecutionDeliveryItemMapper, ExecutionDeliveryItem>
        implements IExecutionDeliveryItemRepository {
}
