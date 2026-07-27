package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectRevenueDO;

public interface ProjectRevenueService {
    ProjectRevenueDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectRevenueDO> page(int pageNum, int pageSize);
    boolean save(ProjectRevenueDO entity);
    boolean updateById(ProjectRevenueDO entity);
    boolean removeById(String id);
}
