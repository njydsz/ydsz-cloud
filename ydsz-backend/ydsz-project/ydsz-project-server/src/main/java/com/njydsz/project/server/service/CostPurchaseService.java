package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.cost.CostPurchase;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface CostPurchaseService {
    CostPurchase getById(String id);
    IPage<CostPurchase> page(int pageNum, int pageSize);
    boolean save(CostPurchase entity);
    boolean updateById(CostPurchase entity);
    boolean removeById(String id);
}
