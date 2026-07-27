package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.cost.CostPurchaseDO;
import com.njydsz.project.domain.repository.cost.ICostPurchaseRepository;
import com.njydsz.project.server.service.CostPurchaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostPurchaseServiceImpl implements CostPurchaseService {
    private final ICostPurchaseRepository repository;

    public CostPurchaseDO getById(String id) { return repository.getById(id); }
    public IPage<CostPurchaseDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CostPurchaseDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CostPurchaseDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
