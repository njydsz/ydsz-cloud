package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItemDO;
import com.njydsz.project.domain.repository.execution.IExecutionDeliveryItemRepository;
import com.njydsz.project.server.service.ExecutionDeliveryItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExecutionDeliveryItemServiceImpl implements ExecutionDeliveryItemService {
    private final IExecutionDeliveryItemRepository repository;

    public ExecutionDeliveryItemDO getById(String id) { return repository.getById(id); }
    public IPage<ExecutionDeliveryItemDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ExecutionDeliveryItemDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ExecutionDeliveryItemDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
