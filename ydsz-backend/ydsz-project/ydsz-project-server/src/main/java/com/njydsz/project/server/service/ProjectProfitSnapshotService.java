package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;

import com.baomidou.mybatisplus.core.metadata.IPage;
public interface ProjectProfitSnapshotService {
    ProjectProfitSnapshot getById(String id);
    IPage<ProjectProfitSnapshot> page(int pageNum, int pageSize);
    boolean save(ProjectProfitSnapshot entity);
    boolean updateById(ProjectProfitSnapshot entity);
    boolean removeById(String id);
}
