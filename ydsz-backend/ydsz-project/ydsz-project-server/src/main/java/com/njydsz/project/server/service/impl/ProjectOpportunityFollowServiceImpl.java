package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollowDO;
import com.njydsz.project.domain.repository.project.IProjectOpportunityFollowRepository;
import com.njydsz.project.server.service.ProjectOpportunityFollowService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectOpportunityFollowServiceImpl implements ProjectOpportunityFollowService {
    private final IProjectOpportunityFollowRepository repository;

    public ProjectOpportunityFollowDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectOpportunityFollowDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectOpportunityFollowDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectOpportunityFollowDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
