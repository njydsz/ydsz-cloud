package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.domain.repository.satisfaction.ISatisfactionRepository;
import com.njydsz.project.server.service.SatisfactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SatisfactionServiceImpl implements SatisfactionService {
    private final ISatisfactionRepository repository;

    public Satisfaction getById(String id) { return repository.getById(id); }
    public IPage<Satisfaction> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Satisfaction e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Satisfaction e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
