package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ExecutionDeliveryStandardService {
    ExecutionDeliveryStandard getById(String id);
    IPage<ExecutionDeliveryStandard> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryStandard entity);
    boolean updateById(ExecutionDeliveryStandard entity);
    boolean removeById(String id);
}
