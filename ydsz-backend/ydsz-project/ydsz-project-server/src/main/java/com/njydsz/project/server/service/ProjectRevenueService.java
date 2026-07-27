package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectRevenue;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectRevenueService {
    ProjectRevenue getById(String id);
    IPage<ProjectRevenue> page(int pageNum, int pageSize);
    boolean save(ProjectRevenue entity);
    boolean updateById(ProjectRevenue entity);
    boolean removeById(String id);
}
