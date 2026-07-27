package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectGateReview;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectGateReviewService {
    ProjectGateReview getById(String id);
    IPage<ProjectGateReview> page(int pageNum, int pageSize);
    boolean save(ProjectGateReview entity);
    boolean updateById(ProjectGateReview entity);
    boolean removeById(String id);
}
