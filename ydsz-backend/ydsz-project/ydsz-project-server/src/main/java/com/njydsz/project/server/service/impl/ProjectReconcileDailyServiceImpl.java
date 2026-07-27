package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectReconcileDailyDO;
import com.njydsz.project.domain.repository.project.IProjectReconcileDailyRepository;
import com.njydsz.project.server.service.ProjectReconcileDailyService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectReconcileDailyServiceImpl implements ProjectReconcileDailyService {
    private final IProjectReconcileDailyRepository repository;

    public ProjectReconcileDailyDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectReconcileDailyDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectReconcileDailyDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectReconcileDailyDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
