package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.domain.repository.execution.IExecutionRiskRepository;
import com.njydsz.project.server.service.ExecutionRiskService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionRiskServiceImpl implements ExecutionRiskService {
    private final IExecutionRiskRepository repository;

    public ExecutionRisk getById(String id) { return repository.getById(id); }
    public IPage<ExecutionRisk> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionRisk e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionRisk e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
