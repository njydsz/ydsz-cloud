package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectExpenseDO;

public interface ProjectExpenseService {
    ProjectExpenseDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectExpenseDO> page(int pageNum, int pageSize);
    boolean save(ProjectExpenseDO entity);
    boolean updateById(ProjectExpenseDO entity);
    boolean removeById(String id);
}
