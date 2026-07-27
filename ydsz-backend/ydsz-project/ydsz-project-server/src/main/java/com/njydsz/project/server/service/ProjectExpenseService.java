package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectExpense;

public interface ProjectExpenseService {
    ProjectExpense getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectExpense> page(int pageNum, int pageSize);
    boolean save(ProjectExpense entity);
    boolean updateById(ProjectExpense entity);
    boolean removeById(String id);
}
