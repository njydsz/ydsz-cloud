package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ExecutionDeliveryItemService {
    ExecutionDeliveryItem getById(String id);
    IPage<ExecutionDeliveryItem> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryItem entity);
    boolean updateById(ExecutionDeliveryItem entity);
    boolean removeById(String id);
}
