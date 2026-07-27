package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshotDO;
import com.njydsz.project.domain.repository.project.IProjectProfitSnapshotRepository;
import com.njydsz.project.server.service.ProjectProfitSnapshotService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectProfitSnapshotServiceImpl implements ProjectProfitSnapshotService {
    private final IProjectProfitSnapshotRepository repository;

    public ProjectProfitSnapshotDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectProfitSnapshotDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectProfitSnapshotDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectProfitSnapshotDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
