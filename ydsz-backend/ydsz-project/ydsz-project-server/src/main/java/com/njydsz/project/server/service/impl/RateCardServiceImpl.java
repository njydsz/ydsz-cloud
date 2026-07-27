package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.rate.RateCard;
import com.njydsz.project.domain.repository.rate.IRateCardRepository;
import com.njydsz.project.server.service.RateCardService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RateCardServiceImpl implements RateCardService {
    private final IRateCardRepository repository;

    public RateCard getById(String id) { return repository.getById(id); }
    public IPage<RateCard> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(RateCard e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(RateCard e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
