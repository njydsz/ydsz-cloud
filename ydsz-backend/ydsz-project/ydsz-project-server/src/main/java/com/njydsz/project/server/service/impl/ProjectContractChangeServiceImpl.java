package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.domain.repository.project.IProjectContractChangeRepository;
import com.njydsz.project.server.service.ProjectContractChangeService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectContractChangeServiceImpl implements ProjectContractChangeService {
    private final IProjectContractChangeRepository repository;

    public ProjectContractChange getById(String id) { return repository.getById(id); }
    public IPage<ProjectContractChange> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContractChange e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContractChange e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
