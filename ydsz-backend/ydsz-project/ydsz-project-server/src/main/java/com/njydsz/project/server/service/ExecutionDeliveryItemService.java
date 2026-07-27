package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItemDO;

public interface ExecutionDeliveryItemService {
    ExecutionDeliveryItemDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionDeliveryItemDO> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryItemDO entity);
    boolean updateById(ExecutionDeliveryItemDO entity);
    boolean removeById(String id);
}
