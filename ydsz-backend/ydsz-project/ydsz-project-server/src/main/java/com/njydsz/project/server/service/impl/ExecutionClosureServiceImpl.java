package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.domain.repository.execution.IExecutionClosureRepository;
import com.njydsz.project.server.service.ExecutionClosureService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionClosureServiceImpl implements ExecutionClosureService {
    private final IExecutionClosureRepository repository;

    public ExecutionClosure getById(String id) { return repository.getById(id); }
    public IPage<ExecutionClosure> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionClosure e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionClosure e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
