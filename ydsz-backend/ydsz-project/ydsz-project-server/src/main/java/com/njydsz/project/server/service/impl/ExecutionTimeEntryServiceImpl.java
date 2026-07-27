package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.domain.repository.execution.IExecutionTimeEntryRepository;
import com.njydsz.project.server.service.ExecutionTimeEntryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionTimeEntryServiceImpl implements ExecutionTimeEntryService {
    private final IExecutionTimeEntryRepository repository;

    public ExecutionTimeEntry getById(String id) { return repository.getById(id); }
    public IPage<ExecutionTimeEntry> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionTimeEntry e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionTimeEntry e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
