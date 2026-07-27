package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.domain.repository.warranty.IWarrantyRepository;
import com.njydsz.project.server.service.WarrantyService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WarrantyServiceImpl implements WarrantyService {
    private final IWarrantyRepository repository;

    public Warranty getById(String id) { return repository.getById(id); }
    public IPage<Warranty> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Warranty e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Warranty e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
