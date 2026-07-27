package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;

public interface ExecutionDeliveryItemService {
    ExecutionDeliveryItem getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ExecutionDeliveryItem> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryItem entity);
    boolean updateById(ExecutionDeliveryItem entity);
    boolean removeById(String id);
}
