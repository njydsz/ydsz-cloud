package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;

public interface ProjectProfitSimulationService {
    ProjectProfitSimulation getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectProfitSimulation> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSimulation entity);
    boolean updateById(ProjectProfitSimulation entity);
    boolean removeById(String id);
}
