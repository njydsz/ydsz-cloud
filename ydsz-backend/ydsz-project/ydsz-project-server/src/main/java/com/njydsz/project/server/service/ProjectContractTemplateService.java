package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractTemplate;

public interface ProjectContractTemplateService {
    ProjectContractTemplate getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractTemplate> page(int pageNum, int pageSize);
    boolean save(ProjectContractTemplate entity);
    boolean updateById(ProjectContractTemplate entity);
    boolean removeById(String id);
}
