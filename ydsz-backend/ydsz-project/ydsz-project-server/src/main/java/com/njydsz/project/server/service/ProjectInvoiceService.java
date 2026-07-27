package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectInvoice;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectInvoiceService {
    ProjectInvoice getById(String id);
    IPage<ProjectInvoice> page(int pageNum, int pageSize);
    boolean save(ProjectInvoice entity);
    boolean updateById(ProjectInvoice entity);
    boolean removeById(String id);
}
