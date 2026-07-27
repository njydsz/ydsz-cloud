package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.domain.repository.cost.ICostPurchaseRepository;
import com.njydsz.project.server.service.CostPurchaseService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostPurchaseServiceImpl implements CostPurchaseService {
    private final ICostPurchaseRepository repository;

    public CostPurchase getById(String id) { return repository.getById(id); }
    public IPage<CostPurchase> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CostPurchase e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CostPurchase e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
