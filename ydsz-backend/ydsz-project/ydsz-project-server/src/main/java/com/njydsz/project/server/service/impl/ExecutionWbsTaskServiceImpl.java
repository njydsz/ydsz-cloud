package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTaskDO;
import com.njydsz.project.domain.repository.execution.IExecutionWbsTaskRepository;
import com.njydsz.project.server.service.ExecutionWbsTaskService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionWbsTaskServiceImpl implements ExecutionWbsTaskService {
    private final IExecutionWbsTaskRepository repository;

    public ExecutionWbsTaskDO getById(String id) { return repository.getById(id); }
    public IPage<ExecutionWbsTaskDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionWbsTaskDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionWbsTaskDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
