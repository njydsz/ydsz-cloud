package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectOpportunityFollowService {
    ProjectOpportunityFollow getById(String id);
    IPage<ProjectOpportunityFollow> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunityFollow entity);
    boolean updateById(ProjectOpportunityFollow entity);
    boolean removeById(String id);
}
