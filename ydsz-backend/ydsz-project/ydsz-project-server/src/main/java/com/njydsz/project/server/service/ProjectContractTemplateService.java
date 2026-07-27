package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractTemplate;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectContractTemplateService {
    ProjectContractTemplate getById(String id);
    IPage<ProjectContractTemplate> page(int pageNum, int pageSize);
    boolean save(ProjectContractTemplate entity);
    boolean updateById(ProjectContractTemplate entity);
    boolean removeById(String id);
}
