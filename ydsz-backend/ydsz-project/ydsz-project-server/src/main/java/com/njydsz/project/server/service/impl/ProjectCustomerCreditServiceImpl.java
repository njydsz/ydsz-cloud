package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.domain.repository.project.IProjectCustomerCreditRepository;
import com.njydsz.project.server.service.ProjectCustomerCreditService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCustomerCreditServiceImpl implements ProjectCustomerCreditService {
    private final IProjectCustomerCreditRepository repository;

    public ProjectCustomerCredit getById(String id) { return repository.getById(id); }
    public IPage<ProjectCustomerCredit> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectCustomerCredit e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectCustomerCredit e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
