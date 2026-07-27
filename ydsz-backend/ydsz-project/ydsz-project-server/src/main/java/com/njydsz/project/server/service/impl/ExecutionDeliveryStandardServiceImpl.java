package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryStandardRepository;
import com.njydsz.project.server.service.ExecutionDeliveryStandardService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionDeliveryStandardServiceImpl implements ExecutionDeliveryStandardService {
    private final IExecutionDeliveryStandardRepository repository;

    public ExecutionDeliveryStandard getById(String id) { return repository.getById(id); }
    public IPage<ExecutionDeliveryStandard> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionDeliveryStandard e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionDeliveryStandard e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
