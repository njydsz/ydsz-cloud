package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectOpportunityDO;

public interface ProjectOpportunityService {
    ProjectOpportunityDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectOpportunityDO> page(int pageNum, int pageSize);
    boolean save(ProjectOpportunityDO entity);
    boolean updateById(ProjectOpportunityDO entity);
    boolean removeById(String id);
}
