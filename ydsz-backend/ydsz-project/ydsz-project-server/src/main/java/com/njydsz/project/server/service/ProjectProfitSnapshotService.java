package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;

public interface ProjectProfitSnapshotService {
    ProjectProfitSnapshot getById(String id);
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectProfitSnapshot> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSnapshot entity);
    boolean updateById(ProjectProfitSnapshot entity);
    boolean removeById(String id);
}
