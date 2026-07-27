package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandardDO;

public interface ExecutionDeliveryStandardService {
    ExecutionDeliveryStandardDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionDeliveryStandardDO> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryStandardDO entity);
    boolean updateById(ExecutionDeliveryStandardDO entity);
    boolean removeById(String id);
}
