package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectChange;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectChangeService {
    ProjectChange getById(String id);
    IPage<ProjectChange> page(int pageNum, int pageSize);
    boolean save(ProjectChange entity);
    boolean updateById(ProjectChange entity);
    boolean removeById(String id);
}
