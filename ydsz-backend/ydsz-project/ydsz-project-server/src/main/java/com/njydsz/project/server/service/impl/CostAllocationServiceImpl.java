package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.domain.repository.cost.ICostAllocationRepository;
import com.njydsz.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CostAllocationServiceImpl implements CostAllocationService {
    private final ICostAllocationRepository repository;

    public CostAllocation getById(String id) { return repository.getById(id); }
    public IPage<CostAllocation> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(CostAllocation e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CostAllocation e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
