package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectGateReviewDO;

public interface ProjectGateReviewService {
    ProjectGateReviewDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectGateReviewDO> page(int pageNum, int pageSize);
    boolean save(ProjectGateReviewDO entity);
    boolean updateById(ProjectGateReviewDO entity);
    boolean removeById(String id);
}
