package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;

public interface ProjectReconcileDailyService {
    ProjectReconcileDaily getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectReconcileDaily> page(int pageNum, int pageSize);
    boolean save(ProjectReconcileDaily entity);
    boolean updateById(ProjectReconcileDaily entity);
    boolean removeById(String id);
}
