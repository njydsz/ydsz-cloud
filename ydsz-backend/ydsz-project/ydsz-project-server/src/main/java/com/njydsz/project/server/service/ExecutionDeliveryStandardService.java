package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;

public interface ExecutionDeliveryStandardService {
    ExecutionDeliveryStandard getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionDeliveryStandard> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryStandard entity);
    boolean updateById(ExecutionDeliveryStandard entity);
    boolean removeById(String id);
}
