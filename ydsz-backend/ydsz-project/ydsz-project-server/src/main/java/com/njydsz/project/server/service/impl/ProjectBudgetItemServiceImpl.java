package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectBudgetItemDO;
import com.njydsz.project.domain.repository.project.IProjectBudgetItemRepository;
import com.njydsz.project.server.service.ProjectBudgetItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectBudgetItemServiceImpl implements ProjectBudgetItemService {
    private final IProjectBudgetItemRepository repository;

    public ProjectBudgetItemDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectBudgetItemDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectBudgetItemDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectBudgetItemDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
