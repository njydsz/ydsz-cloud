package com.njydsz.project.infra.repository.execution;

import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.domain.repository.execution.IExecutionTimeEntryRepository;
import com.njydsz.project.infra.mapper.execution.ExecutionTimeEntryMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * ExecutionTimeEntry Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class ExecutionTimeEntryRepository extends ServiceImpl<ExecutionTimeEntryMapper, ExecutionTimeEntry>
        implements IExecutionTimeEntryRepository {
}
