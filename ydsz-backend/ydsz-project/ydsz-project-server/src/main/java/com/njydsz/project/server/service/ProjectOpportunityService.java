package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunity;

public interface ProjectOpportunityService {
    ProjectOpportunity getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectOpportunity> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunity entity);
    boolean updateById(ProjectOpportunity entity);
    boolean removeById(String id);
}
