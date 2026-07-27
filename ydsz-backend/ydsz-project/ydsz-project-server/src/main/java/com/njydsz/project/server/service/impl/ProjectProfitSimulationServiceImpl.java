package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.domain.repository.project.IProjectProfitSimulationRepository;
import com.njydsz.project.server.service.ProjectProfitSimulationService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectProfitSimulationServiceImpl implements ProjectProfitSimulationService {
    private final IProjectProfitSimulationRepository repository;

    public ProjectProfitSimulation getById(String id) { return repository.getById(id); }
    public IPage<ProjectProfitSimulation> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectProfitSimulation e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectProfitSimulation e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
