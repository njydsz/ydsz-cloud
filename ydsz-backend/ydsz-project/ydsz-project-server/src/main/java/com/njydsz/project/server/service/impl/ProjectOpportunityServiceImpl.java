package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectOpportunityDO;
import com.njydsz.project.domain.repository.project.IProjectOpportunityRepository;
import com.njydsz.project.server.service.ProjectOpportunityService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectOpportunityServiceImpl implements ProjectOpportunityService {
    private final IProjectOpportunityRepository repository;

    public ProjectOpportunityDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectOpportunityDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectOpportunityDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectOpportunityDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
