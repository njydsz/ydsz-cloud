package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContractTemplateDO;
import com.njydsz.project.domain.repository.project.IProjectContractTemplateRepository;
import com.njydsz.project.server.service.ProjectContractTemplateService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectContractTemplateServiceImpl implements ProjectContractTemplateService {
    private final IProjectContractTemplateRepository repository;

    public ProjectContractTemplateDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectContractTemplateDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContractTemplateDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContractTemplateDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
