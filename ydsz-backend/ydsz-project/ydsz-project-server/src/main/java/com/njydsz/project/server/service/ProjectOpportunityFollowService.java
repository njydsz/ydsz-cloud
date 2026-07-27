package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunityFollowDO;

public interface ProjectOpportunityFollowService {
    ProjectOpportunityFollowDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectOpportunityFollowDO> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunityFollowDO entity);
    boolean updateById(ProjectOpportunityFollowDO entity);
    boolean removeById(String id);
}
