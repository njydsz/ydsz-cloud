package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractDO;

public interface ProjectContractService {
    ProjectContractDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractDO> page(int pageNum, int pageSize);
    boolean save(ProjectContractDO entity);
    boolean updateById(ProjectContractDO entity);
    boolean removeById(String id);
}
