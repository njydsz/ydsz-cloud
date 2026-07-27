package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractTemplateDO;

public interface ProjectContractTemplateService {
    ProjectContractTemplateDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractTemplateDO> page(int pageNum, int pageSize);
    boolean save(ProjectContractTemplateDO entity);
    boolean updateById(ProjectContractTemplateDO entity);
    boolean removeById(String id);
}
