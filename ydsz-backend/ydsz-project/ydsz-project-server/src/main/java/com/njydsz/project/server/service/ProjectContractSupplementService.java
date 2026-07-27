package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractSupplement;

public interface ProjectContractSupplementService {
    ProjectContractSupplement getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractSupplement> page(int pageNum, int pageSize);
    boolean save(ProjectContractSupplement entity);
    boolean updateById(ProjectContractSupplement entity);
    boolean removeById(String id);
}
