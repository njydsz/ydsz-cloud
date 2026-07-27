package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectBudgetItem;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectBudgetItemService {
    ProjectBudgetItem getById(String id);
    IPage<ProjectBudgetItem> page(int pageNum, int pageSize);
    boolean save(ProjectBudgetItem entity);
    boolean updateById(ProjectBudgetItem entity);
    boolean removeById(String id);
}
