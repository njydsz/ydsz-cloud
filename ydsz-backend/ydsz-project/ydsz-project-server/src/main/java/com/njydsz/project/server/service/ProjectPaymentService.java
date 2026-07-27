package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectPayment;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectPaymentService {
    ProjectPayment getById(String id);
    IPage<ProjectPayment> page(int pageNum, int pageSize);
    boolean save(ProjectPayment entity);
    boolean updateById(ProjectPayment entity);
    boolean removeById(String id);
}
