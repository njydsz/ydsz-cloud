package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunity;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectOpportunityService {
    ProjectOpportunity getById(String id);
    IPage<ProjectOpportunity> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunity entity);
    boolean updateById(ProjectOpportunity entity);
    boolean removeById(String id);
}
