package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractChange;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectContractChangeService {
    ProjectContractChange getById(String id);
    IPage<ProjectContractChange> page(int pageNum, int pageSize);
    boolean save(ProjectContractChange entity);
    boolean updateById(ProjectContractChange entity);
    boolean removeById(String id);
}
