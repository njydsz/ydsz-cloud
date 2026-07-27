package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.cost.CostAllocation;

public interface CostAllocationService {
    CostAllocation getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<CostAllocation> page(int pageNum, int pageSize);
    boolean save(CostAllocation entity);
    boolean updateById(CostAllocation entity);
    boolean removeById(String id);
}
