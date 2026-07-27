package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.domain.repository.project.IProjectRevenueRepository;
import com.njydsz.project.server.service.ProjectRevenueService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectRevenueServiceImpl implements ProjectRevenueService {
    private final IProjectRevenueRepository repository;

    public ProjectRevenue getById(String id) { return repository.getById(id); }
    public IPage<ProjectRevenue> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectRevenue e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectRevenue e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
