package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractSupplementDO;

public interface ProjectContractSupplementService {
    ProjectContractSupplementDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractSupplementDO> page(int pageNum, int pageSize);
    boolean save(ProjectContractSupplementDO entity);
    boolean updateById(ProjectContractSupplementDO entity);
    boolean removeById(String id);
}
