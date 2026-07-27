package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.domain.repository.alert.IAlertDispatchRepository;
import com.njydsz.project.server.service.AlertDispatchService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertDispatchServiceImpl implements AlertDispatchService {
    private final IAlertDispatchRepository repository;

    public AlertDispatch getById(String id) { return repository.getById(id); }
    public IPage<AlertDispatch> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(AlertDispatch e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(AlertDispatch e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
