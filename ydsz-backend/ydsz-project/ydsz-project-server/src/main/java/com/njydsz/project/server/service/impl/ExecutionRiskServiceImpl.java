package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionRiskDO;
import com.njydsz.project.domain.repository.execution.IExecutionRiskRepository;
import com.njydsz.project.server.service.ExecutionRiskService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionRiskServiceImpl implements ExecutionRiskService {
    private final IExecutionRiskRepository repository;

    public ExecutionRiskDO getById(String id) { return repository.getById(id); }
    public IPage<ExecutionRiskDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionRiskDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionRiskDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
