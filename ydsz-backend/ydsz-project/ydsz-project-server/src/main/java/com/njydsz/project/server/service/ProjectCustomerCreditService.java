package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectCustomerCreditDO;

public interface ProjectCustomerCreditService {
    ProjectCustomerCreditDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectCustomerCreditDO> page(int pageNum, int pageSize);
    boolean save(ProjectCustomerCreditDO entity);
    boolean updateById(ProjectCustomerCreditDO entity);
    boolean removeById(String id);
}
