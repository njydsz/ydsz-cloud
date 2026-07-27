package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSimulationDO;

public interface ProjectProfitSimulationService {
    ProjectProfitSimulationDO getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectProfitSimulationDO> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSimulationDO entity);
    boolean updateById(ProjectProfitSimulationDO entity);
    boolean removeById(String id);
}
