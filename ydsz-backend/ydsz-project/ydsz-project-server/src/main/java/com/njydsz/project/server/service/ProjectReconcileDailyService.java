package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectReconcileDailyDO;

public interface ProjectReconcileDailyService {
    ProjectReconcileDailyDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectReconcileDailyDO> page(int pageNum, int pageSize);
    boolean save(ProjectReconcileDailyDO entity);
    boolean updateById(ProjectReconcileDailyDO entity);
    boolean removeById(String id);
}
