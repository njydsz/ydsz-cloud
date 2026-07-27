package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.cost.CostPurchaseDO;

public interface CostPurchaseService {
    CostPurchaseDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<CostPurchaseDO> page(int pageNum, int pageSize);
    boolean save(CostPurchaseDO entity);
    boolean updateById(CostPurchaseDO entity);
    boolean removeById(String id);
}
