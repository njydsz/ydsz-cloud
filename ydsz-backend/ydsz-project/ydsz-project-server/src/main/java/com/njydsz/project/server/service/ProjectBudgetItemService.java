package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectBudgetItemDO;

public interface ProjectBudgetItemService {
    ProjectBudgetItemDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectBudgetItemDO> page(int pageNum, int pageSize);
    boolean save(ProjectBudgetItemDO entity);
    boolean updateById(ProjectBudgetItemDO entity);
    boolean removeById(String id);
}
