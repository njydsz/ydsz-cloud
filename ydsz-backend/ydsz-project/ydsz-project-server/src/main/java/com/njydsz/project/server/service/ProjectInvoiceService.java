package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectInvoiceDO;

public interface ProjectInvoiceService {
    ProjectInvoiceDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectInvoiceDO> page(int pageNum, int pageSize);
    boolean save(ProjectInvoiceDO entity);
    boolean updateById(ProjectInvoiceDO entity);
    boolean removeById(String id);
}
