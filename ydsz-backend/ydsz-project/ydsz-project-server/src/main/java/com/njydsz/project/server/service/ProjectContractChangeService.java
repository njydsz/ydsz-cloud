package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectContractChangeDO;

public interface ProjectContractChangeService {
    ProjectContractChangeDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectContractChangeDO> page(int pageNum, int pageSize);
    boolean save(ProjectContractChangeDO entity);
    boolean updateById(ProjectContractChangeDO entity);
    boolean removeById(String id);
}
