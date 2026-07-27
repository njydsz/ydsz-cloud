package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectInvoiceDO;
import com.njydsz.project.domain.repository.project.IProjectInvoiceRepository;
import com.njydsz.project.server.service.ProjectInvoiceService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectInvoiceServiceImpl implements ProjectInvoiceService {
    private final IProjectInvoiceRepository repository;

    public ProjectInvoiceDO getById(String id) { return repository.getById(id); }
    public IPage<ProjectInvoiceDO> page(int p, int s) { return repository.page(new Page<>(p, s)); }
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectInvoiceDO e) { return repository.save(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectInvoiceDO e) { return repository.updateById(e); }
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) { return repository.removeById(id); }
}
