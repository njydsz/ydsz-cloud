package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectBudgetItem;

public interface ProjectBudgetItemService {
    ProjectBudgetItem getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectBudgetItem> page(int pageNum, int pageSize);
    boolean save(ProjectBudgetItem entity);
    boolean updateById(ProjectBudgetItem entity);
    boolean removeById(String id);
}
