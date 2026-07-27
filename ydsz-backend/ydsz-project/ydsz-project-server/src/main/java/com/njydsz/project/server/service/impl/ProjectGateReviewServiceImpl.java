package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectGateReviewDO;
import com.njydsz.project.domain.repository.project.IProjectGateReviewRepository;
import com.njydsz.project.server.service.ProjectGateReviewService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectGateReviewServiceImpl implements ProjectGateReviewService {
    private final IProjectGateReviewRepository repository;

    public ProjectGateReviewDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectGateReviewDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectGateReviewDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectGateReviewDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
