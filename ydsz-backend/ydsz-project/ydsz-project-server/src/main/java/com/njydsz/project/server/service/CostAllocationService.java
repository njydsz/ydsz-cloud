package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.cost.CostAllocationDO;

public interface CostAllocationService {
    CostAllocationDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<CostAllocationDO> page(int pageNum, int pageSize);
    boolean save(CostAllocationDO entity);
    boolean updateById(CostAllocationDO entity);
    boolean removeById(String id);
}
