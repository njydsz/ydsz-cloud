package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;

public interface ProjectCustomerCreditService {
    ProjectCustomerCredit getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectCustomerCredit> page(int pageNum, int pageSize);
    boolean save(ProjectCustomerCredit entity);
    boolean updateById(ProjectCustomerCredit entity);
    boolean removeById(String id);
}
